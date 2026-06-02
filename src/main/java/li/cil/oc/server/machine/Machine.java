package li.cil.oc.server.machine;

import li.cil.oc.OpenComputersMod;
import li.cil.oc.Settings;
import li.cil.oc.api.machine.Architecture;
import li.cil.oc.api.machine.Callback;
import li.cil.oc.api.machine.Context;
import li.cil.oc.api.machine.ExecutionResult;
import li.cil.oc.api.machine.LimitReachedException;
import li.cil.oc.api.machine.MachineHost;
import li.cil.oc.api.machine.Value;
import li.cil.oc.api.network.Component;
import li.cil.oc.api.network.ComponentConnector;
import li.cil.oc.api.network.Connector;
import li.cil.oc.api.network.Message;
import li.cil.oc.api.network.Node;
import li.cil.oc.api.network.Visibility;
import li.cil.oc.api.prefab.AbstractManagedEnvironment;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Core machine runtime. Implements the {@link li.cil.oc.api.machine.Machine} interface
 * and drives the architecture execution loop on the computer thread pool.
 *
 * Phase 1 stubs: PacketSender, EventHandler.scheduleClose, SaveHandler, Registry.convert,
 * and canInteract admin checks are all no-ops until their subsystems are ported.
 */
public class Machine extends AbstractManagedEnvironment
    implements li.cil.oc.api.machine.Machine, Runnable {

    // ----------------------------------------------------------------------- //
    // Possible states of the computer and its executor thread.
    // ----------------------------------------------------------------------- //

    public enum State {
        Stopped,
        Starting,
        Restarting,
        Stopping,
        Paused,
        SynchronizedCall,
        SynchronizedReturn,
        Yielded,
        Sleeping,
        Running;

        public static State fromOrdinal(int ordinal) {
            return values()[ordinal];
        }
    }

    // ----------------------------------------------------------------------- //
    // Signals — messages from Java to Lua, sent asynchronously.
    // ----------------------------------------------------------------------- //

    public static class Signal implements li.cil.oc.api.machine.Signal {
        public final String name;
        public final Object[] args;

        public Signal(String name, Object[] args) {
            this.name = name;
            this.args = args;
        }

        @Override public String name() { return name; }
        @Override public Object[] args() { return args; }

        /** Convert signal args through the driver registry (Phase 2 stub: pass through). */
        public Signal convert() {
            return new Signal(name, args);
        }
    }

    // ----------------------------------------------------------------------- //
    // Thread pool shared across all Machine instances.
    // ----------------------------------------------------------------------- //

    private static final ScheduledExecutorService threadPool =
        li.cil.oc.util.ThreadPoolFactory.create("Computer",
            Settings.get() != null ? Settings.get().threads : 2);

    // ----------------------------------------------------------------------- //
    // Instance state
    // ----------------------------------------------------------------------- //

    private final MachineHost host;

    /** State stack; top is the current state. Synchronized on itself. */
    private final Deque<State> state = new ArrayDeque<>();

    private Architecture architecture;
    private int maxComponents;
    private double maxCallBudget = 1.0;
    private volatile double callBudget;
    private boolean hasMemory;

    /** Components discovered via the network. */
    private final Map<String, String> _components = new HashMap<>();
    /** Components that need to be added on the next update tick. */
    private final List<Component> addedComponents = new ArrayList<>();

    /** Users allowed to interact with this machine. */
    private final List<String> _users = Collections.synchronizedList(new ArrayList<>());
    private volatile boolean usersChanged = false;

    /** Error message for crash display. */
    private volatile String message;

    /** Running cost per execution tick. */
    private double cost;

    private final int maxSignalQueueSize;
    private final Queue<Signal> signals = new LinkedList<>();

    /** Ticks left to sleep before resuming. */
    private int remainIdle;
    /** Ticks left to wait before resuming from pause. */
    private int remainingPause;

    private boolean inSynchronizedCall;

    private long worldTime;
    private long uptime;
    private long cpuTotal;
    private long cpuStart;

    // NBT tag key constants
    private static final String STATE_TAG = "state";
    private static final String USERS_TAG = "users";
    private static final String MESSAGE_TAG = "message";
    private static final String COMPONENTS_TAG = "components";
    private static final String ADDRESS_TAG = "address";
    private static final String NAME_TAG = "name";
    private static final String TMP_TAG = "tmp";
    private static final String SIGNALS_TAG = "signals";
    private static final String ARGS_TAG = "args";
    private static final String LENGTH_TAG = "length";
    private static final String ARG_PREFIX_TAG = "arg";
    private static final String UPTIME_TAG = "uptime";
    private static final String CPU_TIME_TAG = "cpuTime";
    private static final String REMAINING_PAUSE_TAG = "remainingPause";

    // ----------------------------------------------------------------------- //

    public Machine(MachineHost host) {
        this.host = host;
        this.state.push(State.Stopped);
        Settings s = Settings.get();
        this.maxSignalQueueSize = s != null ? s.maxSignalQueueSize : 256;
        this.cost = s != null ? s.computerCost * s.tickFrequency : 0;

        double bufferSize = s != null ? s.bufferComputer : 0;
        setNode(li.cil.oc.api.Network.newNode(this, Visibility.Network)
            .withComponent("computer", Visibility.Neighbors)
            .withConnector(bufferSize)
            .create());
    }

    public Connector connector() {
        Node n = node();
        return n instanceof Connector c ? c : null;
    }

    // ----------------------------------------------------------------------- //
    // Machine interface
    // ----------------------------------------------------------------------- //

    @Override
    public MachineHost host() { return host; }

    @Override
    public Architecture architecture() { return architecture; }

    @Override
    public Map<String, String> components() {
        synchronized (_components) { return Collections.unmodifiableMap(new HashMap<>(_components)); }
    }

    @Override
    public String tmpAddress() { return null; /* Phase 3: tmp filesystem */ }

    @Override
    public String lastError() { return message; }

    @Override
    public void setCostPerTick(double value) {
        this.cost = Settings.get() != null ? value * Settings.get().tickFrequency : value;
    }

    @Override
    public double getCostPerTick() {
        return Settings.get() != null ? cost / Settings.get().tickFrequency : cost;
    }

    @Override
    public String[] users() {
        synchronized (_users) { return _users.toArray(String[]::new); }
    }

    @Override
    public long worldTime() { return worldTime; }

    @Override
    public int componentCount() {
        synchronized (_components) {
            double sum = _components.values().stream()
                .mapToDouble(name -> "filesystem".equals(name) ? 0.25 : 1.0).sum();
            sum += addedComponents.stream()
                .mapToDouble(c -> "filesystem".equals(c.name()) ? 0.25 : 1.0).sum();
            return (int) (sum - 1); // -1 for this computer itself
        }
    }

    @Override
    public int maxComponents() { return maxComponents; }

    @Override
    public double upTime() {
        if (uptime < 0) uptime = worldTime + uptime;
        return uptime / 20.0;
    }

    @Override
    public double cpuTime() {
        return (cpuTotal + (System.nanoTime() - cpuStart)) * 1e-9;
    }

    public Map<String, String> getDeviceInfo() { return null; /* Phase 2 */ }

    @Override
    public boolean canInteract(String player) {
        if (Settings.get() == null || !Settings.get().canComputersBeOwned) return true;
        synchronized (_users) {
            if (_users.isEmpty() || _users.contains(player)) return true;
        }
        // Phase 1 stub: skip admin/server checks (NeoForge ServerLifecycleHooks in Phase 2)
        return false;
    }

    @Override
    public boolean isRunning() {
        synchronized (state) {
            State top = state.peek();
            return top != null && top != State.Stopped && top != State.Stopping;
        }
    }

    @Override
    public boolean isPaused() {
        synchronized (state) {
            return state.peek() == State.Paused && remainingPause > 0;
        }
    }

    @Override
    public boolean start() {
        synchronized (state) {
            State top = state.peek();
            if (top == State.Stopped && node() != null && node().network() != null) {
                onHostChanged();
                processAddedComponents();
                verifyComponents();
                Settings s = Settings.get();
                if (s != null && !s.ignorePower) {
                    Connector con = connector();
                    if (con != null && con.globalBuffer() < cost) {
                        crash("gui.Error.NoEnergy");
                        return false;
                    }
                }
                if (architecture == null || maxComponents == 0) {
                    beep("-");
                    crash("gui.Error.NoCPU");
                    return false;
                }
                if (!hasMemory) {
                    beep("-.");
                    crash("gui.Error.NoRAM");
                    return false;
                }
                if (!init()) {
                    beep("--");
                    return false;
                }
                switchTo(State.Starting);
                uptime = 0;
                if (node() != null) node().sendToReachable("computer.started");
                return true;
            } else if (top == State.Paused && remainingPause > 0) {
                remainingPause = 0;
                host.markChanged();
                return true;
            } else if (top == State.Stopping) {
                switchTo(State.Restarting);
                // EventHandler.unscheduleClose stub
                return true;
            }
            return false;
        }
    }

    @Override
    public boolean pause(double seconds) {
        int ticksToPause = Math.max((int) (seconds * 20), 0);
        synchronized (this) {
            synchronized (state) {
                State top = state.peek();
                if (top == State.Stopping || top == State.Stopped) return false;
                if (top == State.Paused && ticksToPause <= remainingPause) return false;
                if (top != State.Paused) {
                    state.push(State.Paused);
                }
                remainingPause = ticksToPause;
                host.markChanged();
                return true;
            }
        }
    }

    @Override
    public boolean stop() {
        synchronized (state) {
            State top = state.peek();
            if (top == State.Stopped || top == State.Stopping) return false;
            state.push(State.Stopping);
            // EventHandler.scheduleClose stub — Phase 2
            return true;
        }
    }

    @Override
    public void onHostChanged() {
        Iterable<net.minecraft.world.item.ItemStack> components = host.internalComponents();
        maxComponents = 0;
        maxCallBudget = 1.0;

        // Phase 2 stub: driver registry not ported yet; maxComponents defaults to a non-zero value
        // to avoid NoCPU crash when there's no driver registry.
        // When driver/Registry is ported, this loop will call Driver.driverFor(stack, host.getClass)
        // to sum Processor.supportedComponents and average CallBudget.getCallBudget.
        Architecture newArch = null;
        // For Phase 1 we use the registered architectures directly
        for (Class<? extends Architecture> archClass : Machine.architectures()) {
            try {
                newArch = archClass.getConstructor(li.cil.oc.api.machine.Machine.class).newInstance(this);
                break;
            } catch (Exception e) {
                OpenComputersMod.LOGGER.warn("Failed instantiating architecture {}.", archClass.getSimpleName(), e);
            }
        }
        if (newArch != architecture) {
            synchronized (this) {
                architecture = newArch;
                if (architecture != null && node() != null && node().network() != null) {
                    architecture.onConnect();
                }
            }
        }
        hasMemory = architecture != null && architecture.recomputeMemory(components);

        // Use default max components until driver registry is ported
        if (maxComponents == 0) maxComponents = 8;
    }

    @Override
    public void consumeCallBudget(double callCost) throws LimitReachedException {
        if (architecture != null && architecture.isInitialized() && !inSynchronizedCall) {
            double clamped = Math.max(0.0, callCost);
            if (clamped > callBudget) throw new LimitReachedException();
            callBudget -= clamped;
        }
    }

    @Override
    public void beep(short frequency, short duration) {
        // PacketSender stub — Phase 5
    }

    @Override
    public void beep(String pattern) {
        // PacketSender stub — Phase 5
    }

    @Override
    public boolean crash(String message) {
        this.message = message;
        synchronized (state) {
            boolean result = stop();
            if (state.peek() == State.Stopping) {
                state.clear();
                state.push(State.Stopping);
            }
            return result;
        }
    }

    @Override
    public boolean signal(String name, Object... args) {
        synchronized (state) {
            State top = state.peek();
            if (top == State.Stopped || top == State.Stopping) return false;
        }
        synchronized (signals) {
            if (signals.size() >= maxSignalQueueSize) return false;
            if (args == null) {
                signals.add(new Signal(name, new Object[0]));
            } else {
                Object[] converted = new Object[args.length];
                for (int i = 0; i < args.length; i++) {
                    converted[i] = convertArg(args[i]);
                }
                signals.add(new Signal(name, converted));
            }
        }
        if (architecture != null) architecture.onSignal();
        return true;
    }

    @Override
    public li.cil.oc.api.machine.Signal popSignal() {
        synchronized (signals) {
            if (signals.isEmpty()) return null;
            return signals.poll().convert();
        }
    }

    @Override
    public Map<String, Callback> methods(Object value) {
        // Phase 2: Callbacks/Registry not yet ported — return empty map
        return Collections.emptyMap();
    }

    @Override
    public Object[] invoke(String address, String method, Object[] args) throws Exception {
        if (node() != null && node().network() != null) {
            // Phase 2 stub: direct component invocation via network node
            throw new IllegalArgumentException("component invocation not yet supported (Phase 2)");
        }
        throw new LimitReachedException();
    }

    @Override
    public Object[] invoke(Value value, String method, Object[] args) throws Exception {
        // Phase 2 stub
        throw new NoSuchMethodException("value invocation not yet supported (Phase 2)");
    }

    @Override
    public void addUser(String name) throws Exception {
        if (_users.size() >= (Settings.get() != null ? Settings.get().maxUsers : 0))
            throw new Exception("too many users");
        synchronized (_users) {
            if (_users.contains(name)) throw new Exception("user exists");
            if (name.length() > (Settings.get() != null ? Settings.get().maxUsernameLength : 32))
                throw new Exception("username too long");
            // Phase 2 stub: skip online-player check (no server reference yet)
            _users.add(name);
            usersChanged = true;
        }
    }

    @Override
    public boolean removeUser(String name) {
        synchronized (_users) {
            boolean success = _users.remove(name);
            if (success) usersChanged = true;
            return success;
        }
    }

    // ----------------------------------------------------------------------- //
    // ManagedEnvironment / update
    // ----------------------------------------------------------------------- //

    @Override
    public boolean canUpdate() { return true; }

    @Override
    public void update() {
        synchronized (state) {
            if (state.peek() == State.Stopped) return;
        }

        processAddedComponents();

        // Update world time
        if (host.world() != null) {
            worldTime = host.world().getGameTime();
        }
        uptime++;

        if (remainIdle > 0) remainIdle--;

        // Reset direct-call budget
        callBudget = maxCallBudget;

        // Power cost check (Phase 8)
        // if (worldTime % tickFrequency == 0) { ... }

        synchronized (state) {
            State top = state.peek();
            switch (top) {
                case Paused -> {
                    if (remainingPause > 0) {
                        remainingPause--;
                        if (remainingPause == 0) {
                            state.pop(); // Paused
                            // Pop whatever was under Paused (e.g. Sleeping) and switch to Yielded
                            if (!state.isEmpty()) state.pop();
                            switchTo(State.Yielded);
                        }
                    }
                }
                case Sleeping -> {
                    if (remainIdle <= 0) {
                        synchronized (signals) {
                            if (!signals.isEmpty()) switchTo(State.Yielded);
                            else switchTo(State.Sleeping);
                        }
                    }
                }
                case Restarting -> {
                    close();
                    if (init()) {
                        switchTo(State.Starting);
                        uptime = 0;
                        if (node() != null) node().sendToReachable("computer.started");
                    }
                }
                case Stopping -> {
                    close();
                    state.clear();
                    state.push(State.Stopped);
                    host.markChanged();
                    if (node() != null) node().sendToReachable("computer.stopped");
                }
                case SynchronizedCall -> {
                    assert architecture != null;
                    inSynchronizedCall = true;
                    try {
                        architecture.runSynchronized();
                        synchronized (state) {
                            if (state.peek() == State.SynchronizedCall) {
                                switchTo(State.SynchronizedReturn);
                            }
                        }
                    } catch (Exception e) {
                        OpenComputersMod.LOGGER.warn("Error in synchronized call.", e);
                        crash("gui.Error.InternalError");
                    } finally {
                        inSynchronizedCall = false;
                    }
                }
                default -> { /* Starting, Yielded, Running — handled by the thread */ }
            }
        }
    }

    // ----------------------------------------------------------------------- //
    // Thread execution (Runnable — called by the thread pool)
    // ----------------------------------------------------------------------- //

    @Override
    public void run() {
        synchronized (this) {
            boolean isSynchronizedReturn;
            synchronized (state) {
                State top = state.peek();
                if (top != State.Yielded && top != State.SynchronizedReturn) return;
                // Phase 1 stub: isGamePaused check omitted (no Minecraft reference)
                isSynchronizedReturn = switchTo(State.Running) == State.SynchronizedReturn;
            }

            cpuStart = System.nanoTime();

            try {
                ExecutionResult result = architecture.runThreaded(isSynchronizedReturn);

                synchronized (state) {
                    switch (state.peek()) {
                        case Running -> {
                            if (result instanceof ExecutionResult.Sleep r) {
                                synchronized (signals) {
                                    if (signals.isEmpty() && r.ticks > 0) {
                                        switchTo(State.Sleeping);
                                        remainIdle = r.ticks;
                                    } else {
                                        switchTo(State.Yielded);
                                    }
                                }
                            } else if (result instanceof ExecutionResult.SynchronizedCall) {
                                switchTo(State.SynchronizedCall);
                            } else if (result instanceof ExecutionResult.Shutdown r) {
                                switchTo(r.reboot ? State.Restarting : State.Stopping);
                            } else if (result instanceof ExecutionResult.Error r) {
                                beep("--");
                                crash(r.message != null ? r.message : "unknown error");
                            }
                        }
                        case Paused -> {
                            state.pop(); // Paused
                            state.pop(); // Running
                            if (result instanceof ExecutionResult.Sleep r) {
                                remainIdle = r.ticks;
                                state.push(State.Sleeping);
                            } else if (result instanceof ExecutionResult.SynchronizedCall) {
                                state.push(State.SynchronizedCall);
                            } else if (result instanceof ExecutionResult.Shutdown r) {
                                state.push(r.reboot ? State.Restarting : State.Stopping);
                            } else if (result instanceof ExecutionResult.Error r) {
                                crash(r.message != null ? r.message : "unknown error");
                            }
                            state.push(State.Paused);
                        }
                        case Stopping -> {
                            state.clear();
                            state.push(State.Stopping);
                        }
                        case Restarting -> { /* Nothing to do */ }
                        default ->
                            throw new AssertionError("Invalid state in executor post-processing: " + state.peek());
                    }
                }
            } catch (Throwable e) {
                OpenComputersMod.LOGGER.warn("Architecture's runThreaded threw an error. This should never happen!", e);
                crash("gui.Error.InternalError");
            } finally {
                cpuTotal += System.nanoTime() - cpuStart;
            }
        }
    }

    // ----------------------------------------------------------------------- //
    // Network callbacks
    // ----------------------------------------------------------------------- //

    @Override
    public void onConnect(Node node) {
        if (node == this.node()) {
            if (architecture != null) architecture.onConnect();
        } else if (node instanceof Component c) {
            addComponent(c);
        }
        host.onMachineConnect(node);
    }

    @Override
    public void onDisconnect(Node node) {
        if (node == this.node()) {
            close();
        } else if (node instanceof Component c) {
            removeComponent(c);
        }
        host.onMachineDisconnect(node);
    }

    @Override
    public void onMessage(Message message) { /* not used */ }

    // ----------------------------------------------------------------------- //
    // NBT persistence
    // ----------------------------------------------------------------------- //

    @Override
    public void load(CompoundTag nbt) {
        synchronized (this) {
            synchronized (state) {
                assert state.peek() == State.Stopped || state.peek() == State.Paused;
                close();
                state.clear();

                super.load(nbt);

                int[] stateArray = nbt.getIntArray(STATE_TAG);
                for (int i = stateArray.length - 1; i >= 0; i--) {
                    state.push(State.fromOrdinal(stateArray[i]));
                }

                ListTag userList = nbt.getList(USERS_TAG, Tag.TAG_STRING);
                for (int i = 0; i < userList.size(); i++) {
                    _users.add(userList.getString(i));
                }

                if (nbt.contains(MESSAGE_TAG)) {
                    message = nbt.getString(MESSAGE_TAG);
                }

                ListTag componentList = nbt.getList(COMPONENTS_TAG, Tag.TAG_COMPOUND);
                synchronized (_components) {
                    for (int i = 0; i < componentList.size(); i++) {
                        CompoundTag tag = componentList.getCompound(i);
                        _components.put(tag.getString(ADDRESS_TAG), tag.getString(NAME_TAG));
                    }
                }

                // tmp filesystem: Phase 3

                if (!state.isEmpty() && isRunning() && init()) {
                    try {
                        architecture.load(nbt);

                        ListTag signalList = nbt.getList(SIGNALS_TAG, Tag.TAG_COMPOUND);
                        synchronized (signals) {
                            for (int i = 0; i < signalList.size(); i++) {
                                CompoundTag signalNbt = signalList.getCompound(i);
                                CompoundTag argsNbt = signalNbt.getCompound(ARGS_TAG);
                                int argsLength = argsNbt.getInt(LENGTH_TAG);
                                Object[] args = new Object[argsLength];
                                for (int j = 0; j < argsLength; j++) {
                                    args[j] = loadSignalArg(argsNbt, ARG_PREFIX_TAG + j);
                                }
                                signals.add(new Signal(signalNbt.getString(NAME_TAG), args));
                            }
                        }

                        uptime = nbt.getLong(UPTIME_TAG);
                        cpuTotal = nbt.getLong(CPU_TIME_TAG);
                        remainingPause = nbt.getInt(REMAINING_PAUSE_TAG);

                        if (state.peek() != State.Restarting) {
                            pause(Settings.get() != null ? Settings.get().startupDelay : 0.5);
                        }
                    } catch (Exception e) {
                        OpenComputersMod.LOGGER.warn("Failed restoring machine state.", e);
                        close();
                    }
                }
            }
        }
    }

    @Override
    public void save(CompoundTag nbt) {
        synchronized (this) {
            assert !isExecuting();

            super.save(nbt);

            synchronized (state) {
                int[] stateArray = new int[state.size()];
                int i = 0;
                for (State s : state) stateArray[i++] = s.ordinal();
                nbt.putIntArray(STATE_TAG, stateArray);
            }

            synchronized (_users) {
                ListTag userList = new ListTag();
                for (String user : _users) userList.add(net.minecraft.nbt.StringTag.valueOf(user));
                nbt.put(USERS_TAG, userList);
            }

            if (message != null) nbt.putString(MESSAGE_TAG, message);

            synchronized (_components) {
                ListTag componentList = new ListTag();
                for (Map.Entry<String, String> entry : _components.entrySet()) {
                    CompoundTag tag = new CompoundTag();
                    tag.putString(ADDRESS_TAG, entry.getKey());
                    tag.putString(NAME_TAG, entry.getValue());
                    componentList.add(tag);
                }
                nbt.put(COMPONENTS_TAG, componentList);
            }

            // tmp filesystem: Phase 3

            if (isRunning() && architecture != null) {
                try {
                    architecture.save(nbt);

                    synchronized (signals) {
                        ListTag signalList = new ListTag();
                        for (Signal signal : signals) {
                            CompoundTag signalNbt = new CompoundTag();
                            signalNbt.putString(NAME_TAG, signal.name);
                            CompoundTag argsNbt = new CompoundTag();
                            argsNbt.putInt(LENGTH_TAG, signal.args.length);
                            for (int j = 0; j < signal.args.length; j++) {
                                saveSignalArg(argsNbt, ARG_PREFIX_TAG + j, signal.args[j]);
                            }
                            signalNbt.put(ARGS_TAG, argsNbt);
                            signalList.add(signalNbt);
                        }
                        nbt.put(SIGNALS_TAG, signalList);
                    }

                    nbt.putLong(UPTIME_TAG, uptime);
                    nbt.putLong(CPU_TIME_TAG, cpuTotal + (System.nanoTime() - cpuStart));
                    nbt.putInt(REMAINING_PAUSE_TAG, remainingPause);
                } catch (Exception e) {
                    OpenComputersMod.LOGGER.warn("Failed saving machine state.", e);
                }
            }
        }
    }

    // ----------------------------------------------------------------------- //
    // Internal helpers
    // ----------------------------------------------------------------------- //

    private boolean init() {
        if (architecture == null) return false;
        try {
            return architecture.initialize();
        } catch (Exception e) {
            OpenComputersMod.LOGGER.warn("Failed to initialize architecture.", e);
            return false;
        }
    }

    private void close() {
        if (architecture != null) {
            try { architecture.close(); } catch (Exception e) {
                OpenComputersMod.LOGGER.warn("Error closing architecture.", e);
            }
        }
        synchronized (signals) { signals.clear(); }
    }

    private boolean isExecuting() {
        synchronized (state) { return state.contains(State.Running); }
    }

    private State switchTo(State value) {
        synchronized (state) {
            State old = state.isEmpty() ? State.Stopped : state.pop();
            state.push(value);
            if (value == State.Yielded || value == State.SynchronizedReturn) {
                remainIdle = 0;
                int delay = Settings.get() != null ? Settings.get().executionDelay : 0;
                threadPool.schedule(this, delay, TimeUnit.MILLISECONDS);
            }
            host.markChanged();
            return old;
        }
    }

    private void addComponent(Component component) {
        synchronized (_components) {
            if (!_components.containsKey(component.address())) {
                addedComponents.add(component);
            }
        }
    }

    private void removeComponent(Component component) {
        synchronized (_components) {
            if (_components.containsKey(component.address())) {
                _components.remove(component.address());
                signal("component_removed", component.address(), component.name());
            }
        }
        addedComponents.remove(component);
    }

    private void processAddedComponents() {
        if (addedComponents.isEmpty()) return;
        for (Component component : addedComponents) {
            if (node() != null && component.canBeSeenFrom(node())) {
                synchronized (_components) {
                    _components.put(component.address(), component.name());
                }
                if (architecture != null && architecture.isInitialized()) {
                    signal("component_added", component.address(), component.name());
                }
            }
        }
        addedComponents.clear();
    }

    private void verifyComponents() {
        if (node() == null || node().network() == null) return;
        List<String> invalid = new ArrayList<>();
        synchronized (_components) {
            for (Map.Entry<String, String> entry : _components.entrySet()) {
                Node n = node().network().node(entry.getKey());
                if (!(n instanceof Component c) || !c.name().equals(entry.getValue())) {
                    if ("filesystem".equals(entry.getValue())) {
                        OpenComputersMod.LOGGER.trace("A component of type '{}' disappeared ({}).", entry.getValue(), entry.getKey());
                    } else {
                        OpenComputersMod.LOGGER.warn("A component of type '{}' disappeared ({}).", entry.getValue(), entry.getKey());
                    }
                    signal("component_removed", entry.getKey(), entry.getValue());
                    invalid.add(entry.getKey());
                }
            }
            for (String addr : invalid) _components.remove(addr);
        }
    }

    private static Object convertArg(Object param) {
        return switch (param) {
            case null -> null;
            case Boolean b -> b;
            case Character c -> (int) c.charValue();
            case Byte b -> b;
            case Short s -> s;
            case Integer i -> i;
            case Long l -> l;
            case Number n -> n.doubleValue();
            case String s -> s;
            case byte[] b -> b;
            case CompoundTag t -> t;
            default -> {
                OpenComputersMod.LOGGER.warn("Trying to push signal with unsupported argument type: {}",
                    param.getClass().getName());
                yield null;
            }
        };
    }

    private static Object loadSignalArg(CompoundTag nbt, String key) {
        if (!nbt.contains(key)) return null;
        Tag tag = nbt.get(key);
        return switch (tag.getId()) {
            case Tag.TAG_BYTE -> {
                byte b = ((net.minecraft.nbt.ByteTag) tag).getAsByte();
                yield b == -1 ? null : (b == 1);
            }
            case Tag.TAG_LONG -> ((net.minecraft.nbt.LongTag) tag).getAsLong();
            case Tag.TAG_DOUBLE -> ((net.minecraft.nbt.DoubleTag) tag).getAsDouble();
            case Tag.TAG_STRING -> tag.getAsString();
            case Tag.TAG_BYTE_ARRAY -> ((net.minecraft.nbt.ByteArrayTag) tag).getAsByteArray();
            case Tag.TAG_LIST -> {
                ListTag list = (ListTag) tag;
                Map<String, String> map = new HashMap<>();
                for (int i = 0; i + 1 < list.size(); i += 2) {
                    map.put(list.getString(i), list.getString(i + 1));
                }
                yield map;
            }
            case Tag.TAG_COMPOUND -> tag;
            default -> null;
        };
    }

    private static void saveSignalArg(CompoundTag nbt, String key, Object value) {
        if (value == null) {
            nbt.putByte(key, (byte) -1);
        } else if (value instanceof Boolean b) {
            nbt.putByte(key, b ? (byte) 1 : (byte) 0);
        } else if (value instanceof Long l) {
            nbt.putLong(key, l);
        } else if (value instanceof Double d) {
            nbt.putDouble(key, d);
        } else if (value instanceof Number n) {
            nbt.putDouble(key, n.doubleValue());
        } else if (value instanceof String s) {
            nbt.putString(key, s);
        } else if (value instanceof byte[] b) {
            nbt.putByteArray(key, b);
        } else if (value instanceof Map<?, ?> m) {
            ListTag list = new ListTag();
            for (Map.Entry<?, ?> entry : m.entrySet()) {
                list.add(net.minecraft.nbt.StringTag.valueOf(String.valueOf(entry.getKey())));
                list.add(net.minecraft.nbt.StringTag.valueOf(String.valueOf(entry.getValue())));
            }
            nbt.put(key, list);
        } else if (value instanceof CompoundTag t) {
            nbt.put(key, t);
        }
    }

    // ----------------------------------------------------------------------- //
    // Static companion (MachineAPI implementation)
    // ----------------------------------------------------------------------- //

    private static final List<Class<? extends Architecture>> registeredArchitectures = new ArrayList<>();

    public static void add(Class<? extends Architecture> archClass) {
        if (!registeredArchitectures.contains(archClass)) {
            try {
                archClass.getConstructor(li.cil.oc.api.machine.Machine.class);
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException("Architecture does not have required constructor.", e);
            }
            registeredArchitectures.add(archClass);
        }
    }

    public static List<Class<? extends Architecture>> architectures() {
        return Collections.unmodifiableList(registeredArchitectures);
    }

    public static String getArchitectureName(Class<? extends Architecture> archClass) {
        Architecture.Name annotation = archClass.getAnnotation(Architecture.Name.class);
        return annotation != null ? annotation.value() : archClass.getSimpleName();
    }
}

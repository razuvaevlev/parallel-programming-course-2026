
package lab2;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.function.BooleanSupplier;

public final class Scheduler {
    boolean logging;
    final List<String> log = new ArrayList<>();
    String deadlock;

    private final List<Runnable> bodies = new ArrayList<>();
    private final List<Worker> workers = new ArrayList<>();
    private final List<SchedLock> locks = new ArrayList<>();
    private final StringBuilder trace = new StringBuilder();

    private final Semaphore schedulerSem = new Semaphore(0);

    private static final ThreadLocal<Worker> CURRENT = new ThreadLocal<>();

    static final class Worker {
        final int id;
        final Runnable body;
        final Semaphore permit = new Semaphore(0);
        Thread thread;
        volatile boolean finished;
        boolean touched;
        boolean blocked;
        SchedLock waitingFor;

        Worker(int id, Runnable body) {
            this.id = id;
            this.body = body;
        }
    }

    public Scheduler() { }

    public void spawn(Runnable body) {
        bodies.add(body);
    }

    void register(SchedLock lock) { locks.add(lock); }

    public void run(Strategy strategy) {
        trace.setLength(0);
        deadlock = null;
        workers.clear();

        for (int i = 0; i < bodies.size(); i++) {
            workers.add(new Worker(i, bodies.get(i)));
        }

        for (Worker w : workers) {
            w.thread = new Thread(() -> {
                CURRENT.set(w);
                CURRENT_SCHEDULER.set(this);
                try {
                    yieldTurn(w);
                    w.body.run();
                } catch (DeadlockAbort ignored) {
                } finally {
                    w.finished = true;
                    schedulerSem.release();
                    CURRENT.remove();
                    CURRENT_SCHEDULER.remove();
                }
            });
            w.thread.start();
        }

        int last = -1;

        while (true) {
            boolean alive = false;
            List<Integer> enabled = new ArrayList<>();

            for (Worker w : workers) {
                if (!w.finished) {
                    alive = true;
                    if (!w.blocked) enabled.add(w.id);
                }
            }

            if (!alive) {
                break;
            }

            if (enabled.isEmpty()) {
                deadlock = heldLocks();
                for (Worker stuck : workers) if (!stuck.finished) {
                    stuck.blocked = false;
                    stuck.permit.release();
                }
                break;
            }

            int next = strategy.pick(enabled, last);
            if (next < 0) {
                deadlock = heldLocks();
                for (Worker stuck : workers) if (!stuck.finished) stuck.permit.release();
                break;
            }
            Worker w = workers.get(next);

            trace.append((char) ('A' + next));
            last = next;
            w.permit.release();

            try {
                schedulerSem.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }

        for (Worker w : workers) {
            try {
                w.thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException(e);
            }
        }
    }

    private String heldLocks() {
        StringBuilder b = new StringBuilder("held={");
        boolean first = true;
        for (SchedLock l : locks) if (l.owner >= 0) {
            if (!first) b.append(", ");
            first = false;
            b.append(l.name).append('=').append((char)('A' + l.owner));
        }
        return b.append('}').toString();
    }

    static void lockPause(Worker w, SchedLock lock) {
        w.blocked = true; w.waitingFor = lock;
        CURRENT_SCHEDULER.get().schedulerSem.release();
        w.permit.acquireUninterruptibly();
        if (CURRENT_SCHEDULER.get().deadlock != null) throw new DeadlockAbort();
        w.blocked = false; w.waitingFor = null;
    }
    static final class DeadlockAbort extends RuntimeException { }
    static Worker currentWorker() { return current(); }
    static Scheduler currentScheduler() { current(); return CURRENT_SCHEDULER.get(); }
    static void wakeLockWaiters(SchedLock lock) {
        for (Worker w : CURRENT_SCHEDULER.get().workers)
            if (w.waitingFor == lock) w.blocked = false;
    }

    public String traceString() {
        return trace.toString();
    }

    private static void yieldTurn(Worker w) {
        w.touched = false;
        w.permit.acquireUninterruptibly();
    }

    private static Worker current() {
        Worker w = CURRENT.get();
        if (w == null) {
            throw new IllegalStateException(
                "Shared access outside Scheduler thread"
            );
        }
        return w;
    }

    static void beforeSharedAccess() {
        Worker w = current();

        if (w.touched) {
            w.touched = false;
            Scheduler s = CURRENT_SCHEDULER.get();
            s.schedulerSem.release();
            w.permit.acquireUninterruptibly();
            if (s.deadlock != null) throw new DeadlockAbort();
        }
    }

    static void afterSharedAccess() {
        current().touched = true;
    }

    static void record(String operation) {
        Worker w = current();
        if (w == null) {
            throw new IllegalStateException(
                "Operation outside Scheduler thread"
            );
        }
        Scheduler s = findScheduler(w);
        if (s.logging) {
            s.log.add((char) ('A' + w.id) + "  " + operation);
        }
    }

    private static Scheduler findScheduler(Worker w) {
        return CURRENT_SCHEDULER.get();
    }

    private static final ThreadLocal<Scheduler> CURRENT_SCHEDULER =
        new ThreadLocal<>();

    public static void point() {
        current().touched = true;
        record("window");
    }

    public static void await(BooleanSupplier cond) {
        Worker w = current();
        while (!cond.getAsBoolean()) {
            w.touched = false;
            w.permit.release();
            w.permit.acquireUninterruptibly();
        }
    }
}

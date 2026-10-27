
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
    private final StringBuilder trace = new StringBuilder();

    private final Semaphore schedulerSem = new Semaphore(0);

    private static final ThreadLocal<Worker> CURRENT = new ThreadLocal<>();

    private static final class Worker {
        final int id;
        final Runnable body;
        final Semaphore permit = new Semaphore(0);
        Thread thread;
        volatile boolean finished;
        boolean touched;

        Worker(int id, Runnable body) {
            this.id = id;
            this.body = body;
        }
    }

    public Scheduler() { }

    public void spawn(Runnable body) {
        bodies.add(body);
    }

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
                    enabled.add(w.id);
                }
            }

            if (!alive) {
                break;
            }

            int next = strategy.pick(enabled, last);
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
            CURRENT_SCHEDULER.get().schedulerSem.release();
            w.permit.acquireUninterruptibly();
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

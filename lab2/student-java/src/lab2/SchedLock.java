package lab2;

/* Этап 2: лок, о котором знает планировщик. Поток, ждущий занятый лок, не попадает в enabled;
 * обычный synchronized / ReentrantLock здесь нельзя — поток уснёт с ходом в руках и всё зависнет.
 * Лог: "lock <имя>", "unlock <имя>". Планировщик должен знать все локи, чтобы печатать held={a:A, b:B}. */
public final class SchedLock {
    final String name;
    int owner = -1;

    /** Создаётся в setup() тестовой программы; зарегистрируйте лок в планировщике s. */
    public SchedLock(Scheduler s, String name) { this.name = name; s.register(this); }

    /** Пауза; если лок занят — ждать, не участвуя в выборе; затем стать владельцем. */
    public void lock() {
        Scheduler.beforeSharedAccess();
        Scheduler.Worker w = Scheduler.currentWorker();
        while (owner >= 0) {
            Scheduler.lockPause(w, this);
        }
        owner = w.id;
        Scheduler.record("lock " + name);
        Scheduler.afterSharedAccess();
    }

    /** Пауза; освободить лок, ждавшие потоки снова могут быть выбраны. */
    public void unlock() {
        Scheduler.beforeSharedAccess();
        Scheduler.Worker w = Scheduler.currentWorker();
        if (owner != w.id) throw new IllegalStateException("unlock by non-owner: " + name);
        owner = -1;
        Scheduler.record("unlock " + name);
        Scheduler.wakeLockWaiters(this);
        Scheduler.afterSharedAccess();
    }
}

package lab2;

import java.util.List;

/* Стратегии выбора следующего потока.
 * FirstAlive — этап 1, Replay — этап 2, RandomPick — этап 3, Dfs — этап 4 (ограничение bound — этап 5).
 * enabled — номера потоков, которые могут сделать шаг, по возрастанию; last — прошлый выбранный (-1 в начале). */
public final class Strategies {
    private Strategies() { }

    /** Всегда первый поток из enabled. */
    public static final class FirstAlive implements Strategy {
        public int pick(List<Integer> enabled, int last) {
            if (enabled.isEmpty()) {
                throw new IllegalStateException("No enabled threads");
            }
            return enabled.get(0);
        }
    }

    /** Проигрывает трассу: на i-м решении выбирает поток trace[i] ('A' -> 0, 'B' -> 1). */
    public static final class Replay implements Strategy {
        private final String trace;
        private int position;
        public Replay(String trace) { this.trace = trace; }

        /** Сбросить позицию в трассе перед прогоном. */
        public void beginRun() { position = 0; }

        public int pick(List<Integer> enabled, int last) {
            if (position >= trace.length()) return -1;
            int id = trace.charAt(position++) - 'A';
            if (!enabled.contains(id)) throw new IllegalArgumentException("Replay selected disabled thread " + trace.charAt(position - 1));
            return id;
        }
    }

    /** Случайный поток: enabled.get(rnd.nextInt(enabled.size())), rnd = new java.util.Random(seed). */
    public static final class RandomPick implements Strategy {
        private final java.util.Random rnd;

        public RandomPick(long seed) { rnd = new java.util.Random(seed); }

        public int pick(List<Integer> enabled, int last) {
            if (enabled.isEmpty()) throw new IllegalStateException("No enabled threads");
            return enabled.get(rnd.nextInt(enabled.size()));
        }
    }

    /** Stateless DFS по дереву выборов: каждый прогон с нуля, повторяет префикс прошлого и берёт следующий вариант.
     *  bound — максимум вытеснений (этап 5); для dfs runner передаёт Integer.MAX_VALUE. */
    public static final class Dfs implements Strategy {
        private final int bound;
        private List<Integer> previousPath = List.of();
        private final List<Integer> path = new java.util.ArrayList<>();
        private final List<List<Integer>> choices = new java.util.ArrayList<>();
        private int preemptions;

        public Dfs(int bound) { this.bound = bound; }

        /** Подготовиться к новому прогону (позиция в пути, счётчик вытеснений). */
        public void beginRun() { path.clear(); choices.clear(); preemptions = 0; }

        public int pick(List<Integer> enabled, int last) {
            List<Integer> candidates = new java.util.ArrayList<>();
            boolean preemption = last >= 0 && enabled.contains(last);
            for (int id : enabled)
                if (!preemption || id == last || preemptions < bound) candidates.add(id);
            if (candidates.isEmpty()) throw new IllegalStateException("No enabled threads");

            int depth = path.size();
            choices.add(candidates);
            int choice = depth < previousPath.size() ? previousPath.get(depth) : 0;
            if (choice >= candidates.size()) choice = 0;
            path.add(choice);
            int selected = candidates.get(choice);
            if (preemption && selected != last) preemptions++;
            return selected;
        }

        /** Выбрать следующий путь; false — дерево обойдено. */
        public boolean endRun() {
            for (int depth = path.size() - 1; depth >= 0; depth--) {
                int choice = path.get(depth);
                if (choice + 1 < choices.get(depth).size()) {
                    List<Integer> next = new java.util.ArrayList<>(path.subList(0, depth));
                    next.add(choice + 1);
                    previousPath = next;
                    return true;
                }
            }
            previousPath = List.of();
            return false;
        }
    }
}

import argparse
import re
from pathlib import Path

import matplotlib.pyplot as plt


def read_benchmark_table(readme_path: Path) -> tuple[list[int], list[tuple[str, list[float | None]]]]:
    lines = readme_path.read_text(encoding="utf-8").splitlines()
    heading_index = next(
        (index for index, line in enumerate(lines) if "Замеры пропускной способности" in line),
        None,
    )
    if heading_index is None:
        raise ValueError("Не найден раздел с замерами пропускной способности")

    table_lines = []
    for line in lines[heading_index + 1 :]:
        if line.strip().startswith("|"):
            table_lines.append(line)
        elif table_lines:
            break

    if len(table_lines) < 3:
        raise ValueError("Таблица замеров отсутствует или не содержит данных")

    rows = [[cell.strip() for cell in line.strip().strip("|").split("|")] for line in table_lines]
    thread_counts = [int(re.search(r"\d+", cell).group()) for cell in rows[0][1:]]
    measurements = []

    for row in rows[2:]:
        label = row[0].replace("**", "").strip()
        values = []
        for cell in row[1:]:
            value = cell.strip()
            values.append(None if value in {"—", "-", ""} else float(value.replace(",", ".")))
        measurements.append((label, values))

    return thread_counts, measurements


def main() -> None:
    script_dir = Path(__file__).resolve().parent
    parser = argparse.ArgumentParser(description="Строит график производительности из таблицы в README.md")
    parser.add_argument("--input", type=Path, default=script_dir / "README.md", help="Путь к README.md")
    parser.add_argument(
        "--output",
        type=Path,
        default=script_dir / "benchmark_summary.png",
        help="Путь для PNG-графика",
    )
    args = parser.parse_args()

    thread_counts, measurements = read_benchmark_table(args.input)
    figure, axis = plt.subplots(figsize=(11, 6))

    for label, values in measurements:
        throughput_millions = [None if value is None else value / 1_000_000 for value in values]
        axis.plot(thread_counts, throughput_millions, marker="o", linewidth=2, label=label)

    axis.set_title("Пропускная способность по числу потоков")
    axis.set_xlabel("Число потоков")
    axis.set_ylabel("Пропускная способность, млн оп/с")
    axis.set_xticks(thread_counts)
    axis.grid(True, axis="y", linestyle="--", alpha=0.35)
    axis.legend(loc="upper center", bbox_to_anchor=(0.5, -0.15), ncol=2, frameon=False)
    figure.tight_layout(rect=(0, 0.12, 1, 1))
    args.output.parent.mkdir(parents=True, exist_ok=True)
    figure.savefig(args.output, dpi=180, bbox_inches="tight")
    print(f"График сохранен: {args.output}")


if __name__ == "__main__":
    main()

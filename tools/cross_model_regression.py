import argparse
import csv
import json
import math
import os
import subprocess
import sys
from pathlib import Path

import numpy as np


def parse_args():
    parser = argparse.ArgumentParser(description="Compare Android and Python P1-P2 grids on a shared scenario.")
    parser.add_argument(
        "--android-repo",
        type=Path,
        default=Path(__file__).resolve().parents[1],
        help="Path to the Android repository root.",
    )
    parser.add_argument(
        "--python-root",
        type=Path,
        default=Path(r"C:\Users\giova\OneDrive\Documents\DOCUMENTS\Scripts\Finantial Awareness_v2"),
        help="Path to the Python simulator root.",
    )
    parser.add_argument(
        "--scenario",
        type=Path,
        default=Path(__file__).resolve().with_name("cross_model_shared_scenario.json"),
        help="Path to the shared cross-model scenario JSON.",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=None,
        help="Directory where comparison artifacts will be written.",
    )
    parser.add_argument(
        "--max-abs-threshold",
        type=float,
        default=1e-9,
        help="Maximum allowed absolute difference per grid point.",
    )
    return parser.parse_args()


def import_python_modules(python_root: Path):
    sys.path.insert(0, str(python_root))
    from config import Config  # type: ignore
    from model import Policy, effective_p4_month, simulate  # type: ignore

    return Config, Policy, effective_p4_month, simulate


def build_python_config(scenario: dict, Config):
    utility_curve = np.array([[point["x"], point["y"]] for point in scenario["utility_curve"]], dtype=float)
    age_curve = np.array([[point["x"], point["y"]] for point in scenario["age_curve"]], dtype=float)
    expenses = np.array(
        [[item["age"], item["amount"], item["utility_offset"]] for item in scenario["one_time_expenses"]],
        dtype=float,
    )
    raw = {
        "model": {
            "start_age": float(scenario["model"]["start_age"]),
            "death_age": float(scenario["model"]["death_age"]),
            "retirement_age": float(scenario["model"]["retirement_age"]),
            "initial_capital_eur": float(scenario["model"]["initial_capital_eur"]),
            "inheritance_eur": float(scenario["model"]["inheritance_eur"]),
            "inheritance_age": float(scenario["model"]["inheritance_age"]),
            "tfr_eur": float(scenario["model"]["tfr_eur"]),
            "tfr_age": float(scenario["model"]["tfr_age"]),
            "terminal_bequest_eur": float(scenario["model"]["terminal_bequest_eur"]),
            "real_annual_return": float(scenario["model"]["real_annual_return"]),
            "real_annual_debt_interest": float(scenario["model"]["real_annual_debt_interest"]),
            "minimum_utility_threshold": float(scenario["model"]["minimum_utility_threshold"]),
            "days_per_month": float(scenario["model"]["days_per_month"]),
        },
        "surplus": [
            {
                "start_age": float(band["start_age"]),
                "end_age": float(band["end_age"]),
                "monthly_eur": float(band["monthly_eur"]),
            }
            for band in scenario["surplus"]
        ],
        "policy": {
            "w": 0.0,
            "p3": float(scenario["policy"]["p3"]),
            "p4_base_age": float(scenario["policy"]["p4_base_age"]),
        },
        "grid": {
            "p1_min": min(float(x) for x in scenario["grid"]["p1_values"]),
            "p1_max": max(float(x) for x in scenario["grid"]["p1_values"]),
            "p1_points": len(scenario["grid"]["p1_values"]),
            "p2_min_age": min(float(x) for x in scenario["grid"]["p2_ages"]),
            "p2_max_age": max(float(x) for x in scenario["grid"]["p2_ages"]),
            "p2_step_months": 12,
        },
        "numerics": {
            "std_epsilon": float(scenario["numerics"]["std_epsilon"]),
        },
    }
    return Config(raw=raw, utility_curve=utility_curve, age_curve=age_curve, one_time_expenses=expenses, source_dir=Path("."))


def compute_python_grid(scenario: dict, config, Policy, effective_p4_month, simulate):
    rows = []
    summaries = []
    start_age = float(scenario["model"]["start_age"])
    p3 = float(scenario["policy"]["p3"])

    for weight in scenario["grid"]["weights"]:
        config.raw["policy"]["w"] = float(weight)
        best_row = None
        for p2_age in scenario["grid"]["p2_ages"]:
            p2_month = int(round((float(p2_age) - start_age) * 12.0))
            p4_month = effective_p4_month(p2_month, config)
            p4_age = start_age + p4_month / 12.0
            for p1 in scenario["grid"]["p1_values"]:
                result = simulate(Policy(float(p1), p2_month, p3, p4_month), config)
                row = {
                    "weight": float(weight),
                    "p1": float(p1),
                    "p2_age": int(p2_age),
                    "p4_age": float(p4_age),
                    "objective": float(result["objective"]),
                    "avg_utility": float(result["mean_happiness"]),
                    "std_dev": float(result["std_happiness"]),
                    "stability_score": float(result["stability_index"]),
                }
                rows.append(row)
                if best_row is None or row["objective"] > best_row["objective"]:
                    best_row = row

        summaries.append(
            {
                "weight": float(weight),
                "best_p1": best_row["p1"],
                "best_p2_age": best_row["p2_age"],
                "best_p4_age": best_row["p4_age"],
                "best_objective": best_row["objective"],
                "best_avg_utility": best_row["avg_utility"],
                "best_std_dev": best_row["std_dev"],
                "best_stability_score": best_row["stability_score"],
            }
        )

    return rows, summaries


def write_csv(path: Path, rows: list[dict]):
    if not rows:
        raise ValueError("Cannot write empty CSV")
    fieldnames = list(rows[0].keys())
    with path.open("w", newline="", encoding="utf-8") as handle:
        writer = csv.DictWriter(handle, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)


def run_android_export(android_repo: Path, scenario_path: Path, output_dir: Path):
    gradlew = android_repo / "gradlew.bat"
    env = os.environ.copy()
    env["CROSS_MODEL_SCENARIO_PATH"] = str(scenario_path)
    env["CROSS_MODEL_OUTPUT_DIR"] = str(output_dir)
    command = [
        str(gradlew),
        "testDebugUnitTest",
        "--tests",
        "com.example.daysurpopt.logic.CrossModelExportTest.exportSharedScenarioGrid",
    ]
    subprocess.run(command, cwd=android_repo, env=env, check=True)


def load_csv_rows(path: Path):
    with path.open("r", encoding="utf-8", newline="") as handle:
        return list(csv.DictReader(handle))


def compare_rows(android_rows: list[dict], python_rows: list[dict]):
    android_map = {
        (float(row["weight"]), float(row["p1"]), int(float(row["p2_age"]))): row
        for row in android_rows
    }
    python_map = {
        (float(row["weight"]), float(row["p1"]), int(float(row["p2_age"]))): row
        for row in python_rows
    }

    if android_map.keys() != python_map.keys():
        missing_android = sorted(python_map.keys() - android_map.keys())
        missing_python = sorted(android_map.keys() - python_map.keys())
        raise RuntimeError(
            f"Grid keys differ. Missing on Android: {missing_android[:5]}, missing on Python: {missing_python[:5]}"
        )

    per_weight = {}
    for key in sorted(android_map.keys()):
        weight = key[0]
        android_value = float(android_map[key]["objective"])
        python_value = float(python_map[key]["objective"])
        diff = android_value - python_value
        bucket = per_weight.setdefault(weight, [])
        bucket.append(diff)

    summaries = {}
    max_abs_diff = 0.0
    for weight, diffs in per_weight.items():
        diffs_array = np.array(diffs, dtype=float)
        max_abs_diff = max(max_abs_diff, float(np.max(np.abs(diffs_array))))
        summaries[str(weight)] = {
            "mean_signed_diff": float(np.mean(diffs_array)),
            "mean_abs_diff": float(np.mean(np.abs(diffs_array))),
            "rmse": float(math.sqrt(np.mean(np.square(diffs_array)))),
            "max_abs_diff": float(np.max(np.abs(diffs_array))),
        }

    return summaries, max_abs_diff


def main():
    args = parse_args()
    android_repo = args.android_repo.resolve()
    python_root = args.python_root.resolve()
    scenario_path = args.scenario.resolve()
    output_dir = (args.output_dir or android_repo / "app" / "build" / "reports" / "cross_model_regression").resolve()
    output_dir.mkdir(parents=True, exist_ok=True)

    with scenario_path.open("r", encoding="utf-8") as handle:
        scenario = json.load(handle)

    Config, Policy, effective_p4_month, simulate = import_python_modules(python_root)
    python_config = build_python_config(scenario, Config)
    python_rows, python_summary = compute_python_grid(
        scenario,
        python_config,
        Policy,
        effective_p4_month,
        simulate,
    )

    python_csv = output_dir / "python_cross_model_grid.csv"
    python_summary_path = output_dir / "python_cross_model_summary.json"
    write_csv(python_csv, python_rows)
    python_summary_path.write_text(json.dumps(python_summary, indent=2), encoding="utf-8")

    run_android_export(android_repo, scenario_path, output_dir)

    android_csv = output_dir / "android_cross_model_grid.csv"
    android_summary_path = output_dir / "android_cross_model_summary.json"
    android_rows = load_csv_rows(android_csv)
    comparison_summary, max_abs_diff = compare_rows(android_rows, python_rows)

    summary = {
        "scenario": str(scenario_path),
        "android_csv": str(android_csv),
        "python_csv": str(python_csv),
        "android_summary": str(android_summary_path),
        "python_summary": str(python_summary_path),
        "per_weight": comparison_summary,
        "max_abs_diff": max_abs_diff,
        "threshold": args.max_abs_threshold,
        "passes": max_abs_diff <= args.max_abs_threshold,
    }
    summary_path = output_dir / "cross_model_regression_summary.json"
    summary_path.write_text(json.dumps(summary, indent=2), encoding="utf-8")
    print(json.dumps(summary, indent=2))

    if max_abs_diff > args.max_abs_threshold:
        raise SystemExit(1)


if __name__ == "__main__":
    main()

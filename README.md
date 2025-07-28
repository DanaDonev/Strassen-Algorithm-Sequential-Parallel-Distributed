# Strassen Algorithm Implementation

This project implements Strassen’s matrix multiplication algorithm using three execution modes:

- ✅ **Sequential**
- ✅ **Parallel (Multithreaded)**
- ✅ **Distributed (MPI-based)**

It is designed to benchmark performance across increasing matrix sizes, using random inputs and reporting execution
times.

---

## Project Structure

| File                       | Description                                  |
|----------------------------|----------------------------------------------|
| `MatrixOperations.java`    | Common matrix utility functions              |
| `StrassenAlgorithmSA.java` | Sequential version of Strassen               |
| `StrassenAlgorithmPA.java` | Parallel version using Java threads          |
| `StrassenAlgorithmDA.java` | Distributed version using MPI                |
| `HelpersDA.java`           | Helper functions for the distributed version |
| `ComparisonTest.java`      | Benchmark runner supporting all modes        |

---

## How to Run

### Prerequisites

- JDK **23.0.1** or later.
- For the **distributed version**, [MPJ Express](http://mpj-express.org/) must be installed.

### Sequential Version

```bash
javac -d out src/*.java
java -Xmx32G -cp out StrassenAlgorithmSA

```

### Parallel Version

```bash
javac -d out src/*.java
java -Xmx32G -cp out StrassenAlgorithmPA
```

### Distributed Version

```bash
# Requires MPI Java bindings
javac -cp "path/to/mpj.jar" -d out src/*.java
mpjrun.bat -np <num_processes> -cp out;path/to/mpj.jar StrassenAlgorithmDA <matrix_size>
```

---

## ComparisonTest.java

The `ComparisonTest` class benchmarks the three versions (sequential, parallel, distributed) by incrementally increasing
matrix sizes and measuring execution time.

### Usage

```bash
java -Xmx32G -cp out ComparisonTest <mode> [num_processes]
```

- `<mode>`: One of `sequential`, `parallel`, `distributed`, or `all`.
- `[num_processes]`: Optional. Used only in distributed/all modes. Defaults to `8`.

Example:

```bash
java -Xmx32G -cp out ComparisonTest all 4
```

### What it does

- Runs each selected mode 3 times per matrix size
- Starts from size 500, increasing by 500
- Writes results to:
    - `results/seq_results.csv`
    - `results/par_results.csv`
    - `results/dist_results.csv`
    - `results/all_results.csv`
- Stops benchmarking a mode if it takes longer than 10 minutes

---

## Features

- **Memory Adaptive**: All versions include memory checking to prevent OutOfMemoryError
- **Hardware Adaptive**: Parallel version uses available CPU cores, distributed version supports any number of processes
- **Random Matrix Generation**: Uses random matrices for testing with entries from 1 to 10
- **Performance Monitoring**: Execution time measurement and memory usage reporting

--- 

## Algorithm Overview

All three modes implement Strassen's algorithm using the same recursive mathematical method:

1. Divide each matrix into 4 submatrices.
2. Compute 7 intermediate matrix multiplications (M1 through M7) instead of 8.
3. Combine results using additions and subtractions to form the final matrix.
4. Recursive application continues until base case is reached or memory threshold forces fallback.

The parallel version executes M1–M7 concurrently using threads.
The distributed version assigns M1–M7 calculations to separate MPI processes.

--- 

## License

This project is open-source.

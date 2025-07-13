
import mpi.*;

import java.util.*;

public class StrassenAlgorithmDA extends MatrixOperations implements HelpersDA {

    private static final double MEMORY_SAFETY_THRESHOLD = 0.9;
    private static final long MIN_FREE_MEMORY_MB = 50;
    private static final StrassenAlgorithmSA sequentialStrassen = new StrassenAlgorithmSA();

    private boolean checkProcessMemory(int matrixSize) {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long availableMemory = maxMemory - usedMemory;

        // Estimate memory needed for matrix operations
        // Each matrix element is 4 bytes (int), need space for multiple matrices
        long estimatedMemoryNeeded = estimateMemoryForMatrixOps(matrixSize);

        long requiredMemory = (long) (estimatedMemoryNeeded / MEMORY_SAFETY_THRESHOLD);
        long minFreeMemoryBytes = MIN_FREE_MEMORY_MB * 1024 * 1024;

        boolean hasEnoughMemory = availableMemory >= requiredMemory
                && availableMemory >= minFreeMemoryBytes;

        if (!hasEnoughMemory) {
            System.err.printf("Process memory check failed:%n");
            System.err.printf("Available memory: %.2f MB%n", availableMemory / (1024.0 * 1024.0));
            System.err.printf("Required memory: %.2f MB%n", requiredMemory / (1024.0 * 1024.0));
            System.err.printf("Estimated operation memory: %.2f MB%n", estimatedMemoryNeeded / (1024.0 * 1024.0));
            System.err.printf("Memory threshold: %.0f%% (matching parallel version)%n", MEMORY_SAFETY_THRESHOLD * 100);
        } else {
           // System.out.printf("Process memory check passed: %.2f MB available, %.2f MB required (%.0f%% threshold)%n",
            //        availableMemory / (1024.0 * 1024.0), requiredMemory / (1024.0 * 1024.0),
             //       MEMORY_SAFETY_THRESHOLD * 100);
        }

        return hasEnoughMemory;
    }

    private long estimateMemoryForMatrixOps(int n) {

        // 3 matrices A, B, C (n x n each), 8 submatrices (n/2 x n/2 each) + 7 M matrices (n/2 x n/2 each)
        // Total: 3 (n x n) and 15 (n/2 x n/2) = 6.75 (n x n)
        long matrixSize = (long) n * n * 4;

        return (long)6.75 * matrixSize;
    }

    private void reportMemoryStatus(String context) {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;

        System.out.printf("[%s] Memory: Used %.1f MB / Max %.1f MB%n",
                context,
                usedMemory / (1024.0 * 1024.0),
                maxMemory / (1024.0 * 1024.0));
    }

    public static void main(String[] args) throws Exception {
        StrassenAlgorithmDA instance = new StrassenAlgorithmDA();
        instance.runDistributed(args);
    }

    public void runDistributed(String[] args) throws Exception {
        MPI.Init(args);

        int rank = MPI.COMM_WORLD.Rank();
        int size = MPI.COMM_WORLD.Size();
        final int ROOT = 0;

        int N = 0;
        int[][] A = null, B = null;

        if (rank == ROOT) {
            System.out.println("Strassen's Algorithm Distributed Implementation (per-process memory adaptive)");
            System.out.println("______________________________________________________________");

            if (args.length > 0) {
                N = Integer.parseInt(args[args.length - 1]);
                if (N % 2 != 0) {
                    A = create(N);
                    B = create(N);
                    System.out.println("Matrix size is not divisible by 2. Cannot do strassen algorithm!");
                    return;
                }
            } else {
                System.out.println("ERROR: Cannot get matrix size!");
                return;
            }
            A = create(N);
            B = create(N);

            reportMemoryStatus("Root-Initial");
        }

        int[] nBuffer = new int[1];

        if (rank == ROOT) {
            nBuffer[0] = N;
        }

        MPI.COMM_WORLD.Bcast(nBuffer, 0, 1, MPI.INT, ROOT);
        N = nBuffer[0];

        reportMemoryStatus("Process-" + rank + "-PreExecution");

        runAdaptiveDistribution(rank, size, N, A, B, ROOT);

        MPI.Finalize();
    }

    private void runAdaptiveDistribution(int rank, int size, int N, int[][] A, int[][] B, int ROOT)
            throws Exception {

        int workers = size - 1;

        if (rank == ROOT) {
            System.out.println("Using distributed computation with " + workers + " workers");

            if (!checkProcessMemory(N)) {
                System.err.println("Root process may have insufficient memory for large matrices");
                System.err.println("Consider using smaller matrix sizes or more processes");
            }

            long startTime = System.nanoTime();

            int newSize = N / 2;
            int[][] A11 = new int[newSize][newSize];
            int[][] A12 = new int[newSize][newSize];
            int[][] A21 = new int[newSize][newSize];
            int[][] A22 = new int[newSize][newSize];
            int[][] B11 = new int[newSize][newSize];
            int[][] B12 = new int[newSize][newSize];
            int[][] B21 = new int[newSize][newSize];
            int[][] B22 = new int[newSize][newSize];

            extract(A, A11, 0, 0);
            extract(A, A12, 0, newSize);
            extract(A, A21, newSize, 0);
            extract(A, A22, newSize, newSize);
            extract(B, B11, 0, 0);
            extract(B, B12, 0, newSize);
            extract(B, B21, newSize, 0);
            extract(B, B22, newSize, newSize);

            int[] taskToWorker = createTaskAssignment(workers);
            distributeTasks(workers, N, A11, A12, A21, A22, B11, B12, B21, B22, taskToWorker);

            int[][][] M = collectResults(N, taskToWorker);

            int[][] C11 = add(subtract(add(M[0], M[3]), M[4]), M[6]);
            int[][] C12 = add(M[2], M[4]);
            int[][] C21 = add(M[1], M[3]);
            int[][] C22 = add(subtract(add(M[0], M[2]), M[1]), M[5]);

            int[][] C = new int[N][N];
            join(C11, C, 0, 0);
            join(C12, C, 0, newSize);
            join(C21, C, newSize, 0);
            join(C22, C, newSize, newSize);

            long endTime = System.nanoTime();

            //printMatrix(A);
            //printMatrix(B);
            //printMatrix(C);

            long duration = endTime - startTime;
            System.out.println("\nExecution time: " + duration + " nanoseconds");
            System.out.println("______________________________________________________________");

        } else {
            // Worker process - handle assigned tasks
            handleWorkerTasksAdaptively(rank, N, ROOT);
        }
    }

    private int[] createTaskAssignment(int workers) {
        int[] taskToWorker = new int[8]; // tasks 1-7, index 0 unused

        if (workers >= 7) {
            for (int task = 1; task <= 7; task++) {
                taskToWorker[task] = task;
            }
        } else {
            for (int task = 1; task <= 7; task++) {
                taskToWorker[task] = ((task - 1) % workers) + 1;
            }

            for (int worker = 1; worker <= workers; worker++) {
                List<Integer> assignedTasks = new ArrayList<>();
                for (int task = 1; task <= 7; task++) {
                    if (taskToWorker[task] == worker) {
                        assignedTasks.add(task);
                    }
                }
                //System.out.println("Worker " + worker + " assigned tasks: " + assignedTasks);
            }
        }

        return taskToWorker;
    }

    private void distributeTasks(int workers, int N, int[][] A11, int[][] A12, int[][] A21, int[][] A22,
                                 int[][] B11, int[][] B12, int[][] B21, int[][] B22, int[] taskToWorker) throws Exception {

        List<Request> requests = new ArrayList<>();

        // Send task count to each worker
        for (int worker = 1; worker <= workers; worker++) {
            int taskCount = 0;
            for (int task = 1; task <= 7; task++) {
                if (taskToWorker[task] == worker) {
                    taskCount++;
                }
            }
            Request req = MPI.COMM_WORLD.Isend(new int[]{taskCount}, 0, 1, MPI.INT, worker, 50);
            requests.add(req);
        }

        // Send tasks and associated matrices
        for (int worker = 1; worker <= workers; worker++) {
            int taskIndex = 0;
            for (int task = 1; task <= 7; task++) {
                if (taskToWorker[task] == worker) {
                    // Send task ID
                    int taskTag = 200 + worker * 10 + taskIndex;
                    Request taskReq = MPI.COMM_WORLD.Isend(new int[]{task}, 0, 1, MPI.INT, worker, taskTag);
                    requests.add(taskReq);

                    // Send required matrix parts
                    int[][][] selectedParts = getMatrixPartsForTask(task, A11, A12, A21, A22, B11, B12, B21, B22);
                    for (int j = 0; j < 4; j++) {
                        int[] flat = selectedParts[j] != null ? HelpersDA.flatten(selectedParts[j]) : new int[N / 2 * N / 2];
                        int matrixTag = 1000 + worker * 100 + taskIndex * 10 + j;
                        Request matrixReq = MPI.COMM_WORLD.Isend(flat, 0, N / 2 * N / 2, MPI.INT, worker, matrixTag);
                        requests.add(matrixReq);
                    }

                    taskIndex++;
                }
            }
        }

        // Wait for all non-blocking sends to complete
        Request[] requestArray = requests.toArray(new Request[0]);
        Request.Waitall(requestArray);
    }


    private int[][][] getMatrixPartsForTask(int task, int[][] A11, int[][] A12, int[][] A21, int[][] A22,
                                            int[][] B11, int[][] B12, int[][] B21, int[][] B22) {
        switch (task) {
            case 1:
                return new int[][][]{A11, A22, B11, B22};
            case 2:
                return new int[][][]{A21, A22, B11, null};
            case 3:
                return new int[][][]{A11, null, B12, B22};
            case 4:
                return new int[][][]{A22, null, B21, B11};
            case 5:
                return new int[][][]{A11, A12, B22, null};
            case 6:
                return new int[][][]{A21, A11, B11, B12};
            case 7:
                return new int[][][]{A12, A22, B21, B22};
            default:
                throw new IllegalArgumentException("Invalid task: " + task);
        }
    }

    private int[][][] collectResults(int N, int[] taskToWorker) throws Exception {
        int[][][] M = new int[7][N / 2][N / 2];

        for (int task = 1; task <= 7; task++) {
            int worker = taskToWorker[task];
            int[] resultFlat = new int[N / 2 * N / 2];
            int expectedTag = 3000 + task;  // Simple: tag = 3000 + task number
            MPI.COMM_WORLD.Recv(resultFlat, 0, N / 2 * N / 2, MPI.INT, worker, expectedTag);
            M[task - 1] = HelpersDA.unflatten(resultFlat, N / 2);
            //System.out.printf("Collected result for task %d from worker %d\n", task, taskToWorker[task]);
        }

        return M;
    }

    private void handleWorkerTasksAdaptively(int rank, int N, int ROOT) throws Exception {
        int submatrixSize = N / 2;

        if (!checkProcessMemory(submatrixSize)) {
            System.err.printf("Worker %d: Memory warning for submatrix size %dx%d%n", rank, submatrixSize, submatrixSize);
        }

        int[] taskCountBuffer = new int[1];
        MPI.COMM_WORLD.Recv(taskCountBuffer, 0, 1, MPI.INT, ROOT, 50);
        int taskCount = taskCountBuffer[0];

        Set<Integer> receivedTasks = new HashSet<>();
        while (receivedTasks.size() < taskCount) {
            Status taskStatus = MPI.COMM_WORLD.Probe(ROOT, MPI.ANY_TAG);
            int tag = taskStatus.tag;

            if (tag >= 200 && tag < 1000) { // Task ID message
                int[] taskBuf = new int[1];
                MPI.COMM_WORLD.Recv(taskBuf, 0, 1, MPI.INT, ROOT, tag);
                int task = taskBuf[0];
                receivedTasks.add(task);

                // Now receive matrices (4 parts)
                int[][][] parts = new int[4][N / 2][N / 2];
                for (int j = 0; j < 4; j++) {
                    int matrixTag = 1000 + rank * 100 + (tag - 200 - rank * 10) * 10 + j;
                    int[] flat = new int[N / 2 * N / 2];
                    MPI.COMM_WORLD.Recv(flat, 0, flat.length, MPI.INT, ROOT, matrixTag);
                    parts[j] = HelpersDA.unflatten(flat, N / 2);
                }

                int[][] result = computeStrassenTask(task, parts);

                int resultTag = 3000 + task;
                MPI.COMM_WORLD.Isend(HelpersDA.flatten(result), 0, N / 2 * N / 2, MPI.INT, ROOT, resultTag);
                //System.out.printf("Worker %d: Completed task %d\n", rank, task);
            } else {
                // Unexpected tag
                System.err.printf("Worker %d: Unexpected tag received: %d\n", rank, tag);
                // Optionally, MPI.COMM_WORLD.Recv(...); to clear buffer
            }
        }


//        for (int i = 0; i < taskCount; i++) {
//            int[] taskBuffer = new int[1];
//            int expectedTag = 200 + rank * 10 + i;
//            MPI.COMM_WORLD.Recv(taskBuffer, 0, 1, MPI.INT, ROOT, expectedTag);
//            int task = taskBuffer[0];
//            System.out.printf("Worker %d: Received task %d (i=%d)\n", rank, task, i);
//
//            int[][][] parts = new int[4][N / 2][N / 2];
//            for (int j = 0; j < 4; j++) {
//                int[] flat = new int[N / 2 * N / 2];
//                int matrixTag = 1000 + rank * 100 + i * 10 + j;
//                MPI.COMM_WORLD.Recv(flat, 0, N / 2 * N / 2, MPI.INT, ROOT, matrixTag);
//                parts[j] = HelpersDA.unflatten(flat, N / 2);
//            }
//
//            int[][] result = computeStrassenTask(task, parts);
//
//            int resultTag = 3000 + task;
//            MPI.COMM_WORLD.Isend(HelpersDA.flatten(result), 0, N / 2 * N / 2, MPI.INT, ROOT, resultTag);
//            System.out.printf("Worker %d: Sent result for task %d with tag %d\n", rank, task, resultTag);
//        }
    }

    private int[][] computeStrassenTask(int task, int[][][] parts) {
        int[][] matA = null, matB = null;

        switch (task) {
            case 1:
                matA = add(parts[0], parts[1]);
                matB = add(parts[2], parts[3]);
                break;
            case 2, 5:
                matA = add(parts[0], parts[1]);
                matB = parts[2];
                break;
            case 3, 4:
                matA = parts[0];
                matB = subtract(parts[2], parts[3]);
                break;
            case 6, 7:
                matA = subtract(parts[0], parts[1]);
                matB = add(parts[2], parts[3]);
                break;
        }

        return sequentialStrassen.multiplySA(matA, matB);
    }
}
import java.util.Scanner;
import java.util.concurrent.RecursiveTask;
import java.util.concurrent.ForkJoinPool;

public class StrassenAlgorithmPA extends MatrixOperations {

    private static final ForkJoinPool forkJoinPool = ForkJoinPool.commonPool();
    private static final double MEMORY_THRESHOLD = 0.9;
    private int maxDepth = -1;
    private static final StrassenAlgorithmSA sequentialStrassen = new StrassenAlgorithmSA();

    public int[][] multiplyParallel(int[][] A, int[][] B) {

        int n = A.length;

        if (!hasEnoughMemoryForParallelStrassen(n)) {
            System.out.println("Insufficient memory for ForkJoin Strassen recursion at size " + n + "x" + n +
                    ". Falling back to conventional multiplication.");
            return multiply(A, B);
        }

        this.maxDepth = calculateMaxRecursionDepth(n);

        System.out.printf("Adaptive max recursion depth (based on memory): %d\n", maxDepth);

        return forkJoinPool.invoke(new StrassenTask(A, B, 0));
    }

    private class StrassenTask extends RecursiveTask<int[][]> {
        private final int[][] A, B;
        private final int depth;

        public StrassenTask(int[][] A, int[][] B, int depth) {
            this.A = A;
            this.B = B;
            this.depth = depth;
        }

        @Override
        protected int[][] compute() {
            int n = A.length;

            boolean lowMemory = !hasEnoughMemoryForParallelStrassen(n);
            if (n == 1 || n % 2 != 0 || lowMemory || depth >= maxDepth) {
                if (lowMemory) {
                    System.out.printf("Falling back to conventional multiply at size %dx%d due to memory.\n", n, n);
                    return multiply(A, B);
                } else if (depth >= maxDepth) {
                    //System.out.printf("Max recursion depth %d reached at size %dx%d. Using sequential Strassen.\n", depth, n, n);
                    return sequentialStrassen.multiplySA(A, B);
                }
                return multiply(A, B);
            }

            int newSize = n / 2;
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

            StrassenTask M1 = new StrassenTask(add(A11, A22), add(B11, B22), depth + 1);
            StrassenTask M2 = new StrassenTask(add(A21, A22), B11, depth + 1);
            StrassenTask M3 = new StrassenTask(A11, subtract(B12, B22), depth + 1);
            StrassenTask M4 = new StrassenTask(A22, subtract(B21, B11), depth + 1);
            StrassenTask M5 = new StrassenTask(add(A11, A12), B22, depth + 1);
            StrassenTask M6 = new StrassenTask(subtract(A21, A11), add(B11, B12), depth + 1);
            StrassenTask M7 = new StrassenTask(subtract(A12, A22), add(B21, B22), depth + 1);

            M1.fork();
            M2.fork();
            M3.fork();
            M4.fork();
            M5.fork();
            M6.fork();
            int[][] m7 = M7.compute();
            int[][] m6 = M6.join();
            int[][] m5 = M5.join();
            int[][] m4 = M4.join();
            int[][] m3 = M3.join();
            int[][] m2 = M2.join();
            int[][] m1 = M1.join();

            int[][] C11 = add(subtract(add(m1, m4), m5), m7);
            int[][] C12 = add(m3, m5);
            int[][] C21 = add(m2, m4);
            int[][] C22 = add(add(subtract(m1, m2), m3), m6);

            int[][] C = new int[n][n];
            joinAll(C11, C, 0, 0);
            joinAll(C12, C, 0, newSize);
            joinAll(C21, C, newSize, 0);
            joinAll(C22, C, newSize, newSize);

            return C;
        }
    }

    private void joinAll(int[][] C, int[][] A, int i, int j) {
        for (int iC = 0; iC < C.length; iC++) {
            for (int jC = 0; jC < C.length; jC++) {
                A[i + iC][j + jC] = C[iC][jC];
            }
        }
    }
    
    private boolean hasEnoughMemoryForParallelStrassen(int n) {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        long usedMemory = totalMemory - freeMemory;
        long availableMemory = maxMemory - usedMemory;

        // 3 matrices A, B, C (n x n each), 8 submatrices (n/2 x n/2 each) + 7 M matrices (n/2 x n/2 each)
        // Total: 3 (n x n) and 15 (n/2 x n/2)
        long elementsNeeded = (long)(6.75 * n * n);
        long bytesNeeded = elementsNeeded * 4; // 4 bytes per int

        long estimatedMemoryNeeded = (long) (bytesNeeded * 2);   // 100% overhead for concurrency

        boolean hasEnough = estimatedMemoryNeeded < (availableMemory * MEMORY_THRESHOLD);

        if (!hasEnough) {
            System.out.printf("Parallel memory check for %dx%d matrices:%n", n, n);
            System.out.printf("  Available memory: %.2f MB%n", availableMemory / (1024.0 * 1024.0));
            System.out.printf("  Estimated needed: %.2f MB%n", estimatedMemoryNeeded / (1024.0 * 1024.0));
            System.out.printf("  Safety threshold: %.0f%% of available%n", MEMORY_THRESHOLD * 100);
            System.out.printf("  Parallel overhead: 75%% (due to concurrent execution)%n");
            System.out.printf("  ForkJoin pool size: %d threads%n", forkJoinPool.getParallelism());
        }

        return hasEnough;
    }

    private int calculateMaxRecursionDepth(int n) {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long usedMemory = totalMemory - freeMemory;
        long availableMemory = runtime.maxMemory() - usedMemory;

        double safeMemory = availableMemory * MEMORY_THRESHOLD;
        double denom = 105.0 * n * n;

        if (denom <= 0 || safeMemory <= 0) return 0;

        double ratio = safeMemory / denom;
        return Math.max(0, (int) Math.floor(0.5 * (Math.log(ratio) / Math.log(2))));
    }


    public static void main(String[] args) {
        System.out.println("Strassen's Algorithm Parallel (ForkJoin-Based) Implementation (memory adaptive)");
        System.out.println("______________________________________________________________");

        Scanner scanner = new Scanner(System.in);
        System.out.print("Please specify the size of the matrices: ");
        int N = scanner.nextInt();
        scanner.close();

        int[][] A = create(N);
        int[][] B = create(N);

        StrassenAlgorithmPA strassen = new StrassenAlgorithmPA();

        long startTime = System.nanoTime();
        int[][] C = strassen.multiplyParallel(A, B);
        long endTime = System.nanoTime();

//        printMatrix(A);
//        printMatrix(B);
//        printMatrix(C);

        System.out.println("\nExecution time: " + (endTime - startTime) + " nanoseconds");
    }
}

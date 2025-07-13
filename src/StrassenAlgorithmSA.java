import java.util.Scanner;

public class StrassenAlgorithmSA extends MatrixOperations {

    private static final double MEMORY_THRESHOLD = 0.9;
    
    public int[][] multiplySA(int[][] A, int[][] B) {

        int n = A.length;
        int[][] C = new int[n][n];

        if (n == 1) {
            C[0][0] = A[0][0] * B[0][0];
        } else if (n % 2 == 0) {
            
            if (!hasEnoughMemoryForStrassen(n)) {
                System.out.println("Insufficient memory for Strassen recursion at size " + n + 
                                 "x" + n + ". Falling back to conventional multiplication.");
                return multiply(A, B);
            }

            int[][] A11 = new int[n / 2][n / 2];
            int[][] A12 = new int[n / 2][n / 2];
            int[][] A21 = new int[n / 2][n / 2];
            int[][] A22 = new int[n / 2][n / 2];
            int[][] B11 = new int[n / 2][n / 2];
            int[][] B12 = new int[n / 2][n / 2];
            int[][] B21 = new int[n / 2][n / 2];
            int[][] B22 = new int[n / 2][n / 2];

            extract(A, A11, 0, 0);
            extract(A, A12, 0, n / 2);
            extract(A, A21, n / 2, 0);
            extract(A, A22, n / 2, n / 2);

            extract(B, B11, 0, 0);
            extract(B, B12, 0, n / 2);
            extract(B, B21, n / 2, 0);
            extract(B, B22, n / 2, n / 2);

            // M1 = (A11 + A22) * (B11 + B22)
            int[][] M1 = multiplySA(add(A11, A22), add(B11, B22));
            // M2 = (A21 + A22) * B11
            int[][] M2 = multiplySA(add(A21, A22), B11);
            // M3 = A11 * (B12 - B22)
            int[][] M3 = multiplySA(A11, subtract(B12, B22));
            // M4 = A22 * (B21 − B11)
            int[][] M4 = multiplySA(A22, subtract(B21, B11));
            // M5 = (A11 + A12) * B22
            int[][] M5 = multiplySA(add(A11, A12), B22);
            // M6 = (A21 - A11) * (B11 + B12)
            int[][] M6 = multiplySA(subtract(A21, A11), add(B11, B12));
            // M7 = (A12 - A22) * (B21 + B22)
            int[][] M7 = multiplySA(subtract(A12, A22), add(B21, B22));

            // C11 = M1 + M4 − M5 + M7
            int[][] C11 = add(subtract(add(M1, M4), M5), M7);
            // C12 = M3 + M5
            int[][] C12 = add(M3, M5);
            // C21 = M2 + M4
            int[][] C21 = add(M2, M4);
            // C22 = M1 − M2 + M3 + M6
            int[][] C22 = add(add(subtract(M1, M2), M3), M6);

            join(C11, C, 0, 0);
            join(C12, C, 0, n / 2);
            join(C21, C, n / 2, 0);
            join(C22, C, n / 2, n / 2);
        } else {
            C = multiply(A, B);
        }

        return C;
    }

    private static boolean hasEnoughMemoryForStrassen(int n) {
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

        int depth = (int)(Math.log(n) / Math.log(2));
        
        // Conservative estimate: 50% overhead for temporary objects and GC headroom
        long estimatedMemoryNeeded = (long)(depth * bytesNeeded * 1.5);
        
        boolean hasEnoughMemory = estimatedMemoryNeeded < (availableMemory * MEMORY_THRESHOLD);
        
        if (!hasEnoughMemory) {
            System.out.printf("Memory check for %dx%d matrices:%n", n, n);
            System.out.printf("  Available memory: %.2f MB%n", availableMemory / (1024.0 * 1024.0));
            System.out.printf("  Estimated needed: %.2f MB%n", estimatedMemoryNeeded / (1024.0 * 1024.0));
            System.out.printf("  Safety threshold: %.0f%% of available%n", MEMORY_THRESHOLD * 100);
        }
        
        return hasEnoughMemory;
    }

    public static void main(String[] args) {

        System.out.println("Strassen's Algorithm Sequential Implementation (memory adaptive)");
        System.out.println("______________________________________________________________");
        
        StrassenAlgorithmSA s = new StrassenAlgorithmSA();

        Scanner scanner = new Scanner(System.in);
        System.out.print("Please specify the size of the matrices: ");
        int N = scanner.nextInt();
        scanner.close();

        if (!hasEnoughMemoryForStrassen(N)) {
            System.out.println("Insufficient memory for storing and multiplying matrices " + N + "x" + N);
            return;
        }
        // Display initial memory status
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory();
        long freeMemory = runtime.freeMemory();
        long maxMemory = runtime.maxMemory();
        
//        System.out.println("\nMemory Status Before Execution:");
//        System.out.printf("  Total Memory: %.2f MB%n", totalMemory / (1024.0 * 1024.0));
//        System.out.printf("  Free Memory:  %.2f MB%n", freeMemory / (1024.0 * 1024.0));
//        System.out.printf("  Max Memory:   %.2f MB%n", maxMemory / (1024.0 * 1024.0));

        //CHECKING WITH IDENTITY MATRIX
        //int[][] A = createIdentity(N);

        int[][] A = create(N);
        int[][] B = create(N);

        long startTime = System.nanoTime();
        int[][] C = s.multiplySA(A, B);
        long endTime = System.nanoTime();

//        printMatrix(A);
//        printMatrix(B);
//        printMatrix(C);

        System.out.println("\nExecution time: " + "\033[0;1m" + (endTime - startTime) + " nanoseconds.\n______________________________________________________________");
    }
}

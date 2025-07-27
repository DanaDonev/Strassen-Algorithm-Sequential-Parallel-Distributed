import java.io.FileWriter;
import java.io.IOException;
import java.lang.ProcessBuilder;

public class ComparisonTest {

    public static void main(String[] args) {
        if (args.length < 1 || args.length > 2) {
            System.out.println("Usage: java -Xmx32G -cp out ComparisonTest <mode> [num_processes]");
            System.out.println("Modes: sequential, parallel, distributed, all");
            System.out.println("For 'distributed' or 'all' modes, optionally specify number of processes (default = 8)");
            System.out.println("Note: You may need to increase the Java heap size with -Xmx (e.g., -Xmx32G) for large matrices.");
            return;
        }

        String mode = args[0].toLowerCase();
        String[] modesToRun = switch (mode) {
            case "all" -> new String[] {"sequential", "parallel", "distributed"}; //"sequential",
            default -> new String[] {mode};
        };

        int numProcesses = 8;
        if ((mode.equals("distributed") || mode.equals("all")) && args.length == 2) {
            try {
                numProcesses = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid number of processes. Must be an integer.");
                return;
            }
        }

        int size = 500;
        long maxAllowedTime = 600_000_000_000L; // 10 minutes

        new java.io.File("results").mkdirs();

        while (true) {
            int[][] A = MatrixOperations.create(size);
            int[][] B = MatrixOperations.create(size);
            long avgTime = 0;
            long avgSeq = 0;
            long avgPar = 0;
            long avgDist = 0;
            boolean shouldStop = false;

            for (String currentMode : modesToRun) {
                System.out.println("Testing size: " + size + " (" + currentMode + ")");
                long totalTime = 0;

                for (int i = 0; i < 3; i++) {
                    long time = 0;
                    int[][] result = null;

                    switch (currentMode) {
                        case "sequential": {
                            long start = System.nanoTime();
                            StrassenAlgorithmSA sa = new StrassenAlgorithmSA();
                            result = sa.multiplySA(A, B);
                            long end = System.nanoTime();
                            time = end - start;
                        }
                        break;
                        case "parallel": {
                            long start = System.nanoTime();
                            StrassenAlgorithmPA pa = new StrassenAlgorithmPA();
                            result = pa.multiplyParallel(A, B);
                            long end = System.nanoTime();
                            time = end - start;
                        }
                        break;
                        case "distributed": {
                            System.out.println("Number of processes: " + String.valueOf(numProcesses));
                            ProcessBuilder pb = new ProcessBuilder(
                                    //mpjrun.bat -np <num> -cp <classpath> <MainClass> <args...>
                                    "mpjrun.bat", "-Xmx32G", "-np", String.valueOf(numProcesses),
                                    "-cp", "out;C:/Program Files/mpj-v0_44/lib/mpj.jar", "StrassenAlgorithmDA",
                                    Integer.toString(size)
                            );
                            pb.redirectErrorStream(true);

                            Process process;
                            try {
                                process = pb.start();
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }

                            long distime = 0;
                            try (var reader = new java.io.BufferedReader(new java.io.InputStreamReader(process.getInputStream()))) {
                                String line;
                                while ((line = reader.readLine()) != null) {
                                    if (line.contains("Execution time:")) {
                                        try {
                                            String[] parts = line.split(":");
                                            String msString = parts[1].trim().split(" ")[0];
                                            distime = Long.parseLong(msString);
                                        } catch (Exception ex) {
                                            System.err.println("Failed to parse distributed time: " + ex.getMessage());
                                        }
                                    }
                                }
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }

                            int exitCode = 0;
                            try {
                                exitCode = process.waitFor();
                            } catch (InterruptedException e) {
                                throw new RuntimeException("MPI process interrupted", e);
                            }

                            if (exitCode != 0) {
                                System.err.println("MPI process exited with code: " + exitCode);
                            }

                            if (distime == 0) {
                                System.err.println("Warning: Distributed time not captured. Using fallback wall time.");
                            }

                            time = distime;

                        }
                        break;
                        default:
                            System.out.println("Invalid mode: " + mode);
                            return;
                    }

                    totalTime += time;
                    System.out.printf("Run %d took %d ms\n", i + 1, time / 1_000_000);

                    System.gc(); // Encourage GC between matrix multiplication runs
                }

                avgTime = totalTime / 3;
                System.out.printf("Average time for size %d: %d ms\n", size, avgTime / 1_000_000);

                switch (currentMode) {
                    case "sequential" -> avgSeq = avgTime;
                    case "parallel" -> avgPar = avgTime;
                    case "distributed" -> avgDist = avgTime;
                }

                if (avgTime > maxAllowedTime) {
                    System.out.printf("Stopping test: %s version exceeded 10-minute limit.\n", currentMode);
                    shouldStop = true;
                    break;
                }
            }
            String fileName = switch (mode) {
                case "sequential" -> "results/seq_results.csv";
                case "parallel" -> "results/par_results.csv";
                case "distributed" -> "results/dist_results.csv";
                case "all" -> "results/all_results.csv";
                default -> "results/unknown.csv";
            };

            if (shouldStop) break;

            try (FileWriter writer = new FileWriter(fileName, true)) {
                if(mode.equals("all")){
                    writer.write(String.format("%d,%d,%d,%d\n", size, avgSeq, avgPar, avgDist));
                } else {
                    writer.write(String.format("%d,%d\n", size, avgTime));
                }
                System.out.println("Saved result to: " + fileName);
            } catch (IOException e) {
                System.err.println("Failed to write to file: " + e.getMessage());
            }

            size += 500;
        }
    }
}
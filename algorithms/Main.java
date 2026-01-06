import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class Main {

    public static void main(String[] args) throws Exception {
        // To disable seeding and use truly random values, uncomment the line below:
        SeededRandom.setUseSeed(false);
        
        SeededRandom.initFromArgs(args);
            System.out.println("Starting experiments...");
            
            ExperimentRunner.main(args);

        boolean figure = generateFigures();

        if (figure) {
            System.out.println("Figures generated in results/figures/");
        } else {
            System.out.println("Figures not generated.");
        }
    }

        private static boolean generateFigures() {
            try {
                ProcessBuilder pb = new ProcessBuilder("python3", "--version");
                pb.redirectErrorStream(true);
                Process process = pb.start();
                int exitCode = process.waitFor();

                if (exitCode != 0) {
                    System.out.println("Python3 not found. Skipping figure generation.");
                    System.out.println("To generate figures manually: cd generators && python3 generate_paper_figures.py");
                    return false;
                }

                // Check Python deps (avoid running the script just to crash with a traceback)
                pb = new ProcessBuilder("python3", "-c", "import pandas as pd");
                pb.redirectErrorStream(true);
                process = pb.start();
                exitCode = process.waitFor();
                if (exitCode != 0) {
                    System.out.println("Python deps not found (missing 'pandas'). Skipping figure generation.");
                    System.out.println("To enable figures: pip3 install pandas");
                    System.out.println("Then rerun: cd generators && python3 generate_paper_figures.py --auto");
                    return false;
                }

                pb = new ProcessBuilder("python3", "generate_paper_figures.py", "--auto");
                pb.directory(new File("../generators"));
                pb.redirectErrorStream(true);
                process = pb.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                    }
                }

                exitCode = process.waitFor();
                if (exitCode != 0) {
                    System.out.println("Figure generation exited with code: " + exitCode);
                    return false;
                }
                return true;
            } catch (Exception e) {
                System.out.println("Error generating figures: " + e.getMessage());
                System.out.println("To generate figures manually: cd generators && python3 generate_paper_figures.py");
                return false;
            }
        }
}
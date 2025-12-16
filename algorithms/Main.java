import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;

public class Main {

    public static void main(String[] args) throws Exception {
        SeededRandom.initFromArgs(args);
        System.out.println("Starting experiments...");

        ExperimentRunner.main(args);

        generateFigures();

        System.out.println("Done. Results: results/ , Figures: results/figures/");
    }

    private static void generateFigures() {
        try {
            ProcessBuilder pb = new ProcessBuilder("python3", "--version");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            int exitCode = process.waitFor();

            if (exitCode != 0) {
                System.out.println("Python3 not found. Skipping figure generation.");
                System.out.println("To generate figures manually: cd generators && python3 generate_paper_figures.py");
                return;
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
            }
        } catch (Exception e) {
            System.out.println("Error generating figures: " + e.getMessage());
            System.out.println("To generate figures manually: cd generators && python3 generate_paper_figures.py");
        }
    }
}
package bgu.ds.local;

public class Main {
    public static void main(String[] args) {
        // args = [inFilePath, outFilePath, tasksPerWorker, -t (terminate, optional)]
        String[] inFilesPath = args[0].split(",");
        String[] outFilesPath = args[1].split(",");
        int tasksPerWorker = Integer.parseInt(args[2]);
        boolean terminate = args.length > 3 && args[3].equals("-t");

        LocalApp localApp = LocalApp.getInstance();
        localApp.start(inFilesPath, outFilesPath, tasksPerWorker, terminate);
    }
}

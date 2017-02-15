package beast.app.tools;

import beast.app.beauti.Beauti;
import beast.app.beauti.BeautiConfig;
import beast.app.beauti.BeautiDoc;
import beast.app.draw.BEASTObjectDialog;
import beast.app.draw.BEASTObjectPanel;
//import beast.app.util.Application; //Might be a part of BEASTLabs? Yes
//import beast.app.util.ConsoleApp;
import beast.app.util.XMLFile;
import beast.core.BEASTObject;
import jam.util.IconUtils;

import java.io.*;
import java.util.ArrayList;

/**
 * Created by andre on 15/02/17.
 */
public class ModelComparisonCalculator {

    //The following code started off as a direct copy from Remco's Model_Selection package
    //private static ConsoleApp app;

    public static void main(final String[] args) throws Exception {
       // Application main = null;

            // create the class with application that we want to launch

          /*
            PathSamplerFromFile sampler = new PathSamplerFromFile();

            if (args.length == 0) {
                // try the GUI version

                // need to set the ID of the BEAST-object
                sampler.setID("PathSampler");

                // then initialise
                sampler.initAndValidate();

                // create BeautiDoc and beauti configuration
                BeautiDoc doc = new BeautiDoc();
                doc.beautiConfig = new BeautiConfig();
                doc.beautiConfig.initAndValidate();

                // suppress a few inputs that we don't want to expose to the user
                doc.beautiConfig.suppressBEASTObjects.add(sampler.getClass().getName() + ".mcmc");
                doc.beautiConfig.suppressBEASTObjects.add(sampler.getClass().getName() + ".value");
                doc.beautiConfig.suppressBEASTObjects.add(sampler.getClass().getName() + ".hosts");

                // check wheter the model1Input is correctly set up
                String fileSep = System.getProperty("file.separator");
                if (!sampler.model1Input.get().exists()) {
                    sampler.model1Input.setValue(new XMLFile(Beauti.g_sDir + fileSep + "model.xml"), sampler);
                }

                // create panel with entries for the application
                BEASTObjectPanel panel = new BEASTObjectPanel(sampler, sampler.getClass(), doc);

                // wrap panel in a dialog
                BEASTObjectDialog dialog = new BEASTObjectDialog(panel, null);

                // show the dialog
                if (dialog.showDialog()) {
                    dialog.accept(sampler, doc);
                    // create a console to show standard error and standard output
                    app = new ConsoleApp("PathSampler",
                            "Path Sampler: " + sampler.model1Input.get().getPath(),
                            IconUtils.getIcon(beast.app.to  ols.PathSampleAnalyser.class, "ps.png"));
                    sampler.initAndValidate();
                    sampler.run();
                }
                return;
            }
            */

            // Command line version
            File[] inputFiles = new File[args.length];
            boolean accessProblem = false;

            for (int i = 0; i < args.length; i++){
                inputFiles[i] = new File(args[i]);
                if( ! inputFiles[i].canRead()) {
                    accessProblem = true;
                    System.out.println("Unable to read file " + i);
                }
            }
            if (accessProblem){
                System.out.println("Please check that the files you are trying to use exist and that there are no typos. The files must also be accessible by Java.");
            }
            else{
                System.out.println("Starting to read from files");
                ArrayList<Double>[][] valuesFromFiles = new ArrayList[inputFiles.length][2];

                try {
                    for(int i = 0; i < inputFiles.length; i++){
                        valuesFromFiles[i] = extractValuesFromFile(inputFiles[i]);
                    }

                    //Have successfully read a value for beta and U for each line in each input file
                    //Now perform the analysis of the values
                    if(inputFiles.length == 1){
                       oneFileAnalysis(valuesFromFiles[0]);
                    }
                    else if (inputFiles.length == 2){
                        twoFileAnalysis(valuesFromFiles[0], valuesFromFiles[1]);
                    }

                }
                catch(Exception e){
                    System.out.println("Had an issue while trying to read from one or both of the input files.");
                }
            }


    }

    private static ArrayList<Double>[] extractValuesFromFile(File inputFile) throws Exception{

        ArrayList<Double>[] retValue = new ArrayList[2];
        ArrayList<Double> betaValues = new ArrayList<>();
        ArrayList<Double> UValues = new ArrayList<>();

        int betaColumnIndex = -1, UColumnIndex = -1;
        Reader read = new FileReader(inputFile);
        BufferedReader buff = new BufferedReader(read);

        if(buff.ready()){
            String line;
            while ((line = buff.readLine()) != null) {
                if (line.substring(0,1).equals("#")){

                    //This is a comment line
                }
                else{
                    if (betaColumnIndex == -1 || UColumnIndex == -1){
                        //Can only assume that this line is the header line, so find the columns we are interested in

                        String[] colNames = line.split("\\t");
                        for (int i = 0; i < colNames.length; i++){
                            if (colNames[i].toLowerCase().equals("betavalue") || colNames[i].toLowerCase().equals("beta.value")){
                                betaColumnIndex = i;
                            }
                            else if(colNames[i].toLowerCase().equals("uvalue") || colNames[i].toLowerCase().equals("u.value")){
                                UColumnIndex = i;
                            }
                        }


                        //User error handling
                        if (betaColumnIndex == -1){
                            System.out.println("PROBLEM: Couldn't find the column for beta in the log file: " + inputFile);
                        }
                        if (UColumnIndex == -1){
                            System.out.println("PROBLEM: Couldn't find the column for U in the log file: " + inputFile);
                        }
                        if(betaColumnIndex == -1 || UColumnIndex == -1){
                            throw new NoSuchFieldException();
                        }
                    }
                    else{
                        //Extract values from this line
                        String[] lineValues = line.split("\\t");
                        betaValues.add(Double.parseDouble(lineValues[betaColumnIndex]));
                        UValues.add(Double.parseDouble(lineValues[UColumnIndex]));
                    }
                }
            }
        }
        retValue[0] = betaValues;
        retValue[1] = UValues;
        return retValue;
    }



    private static void oneFileAnalysis(ArrayList<Double>[] valuesFromFile){
        //NEED TO HANDLE BOTH ONEWAY AND BOTHWAYS ANALYSIS
        double startingBetaValue = valuesFromFile[0].get(0);
        double endingBetaValue = valuesFromFile[0].get(valuesFromFile[0].size() - 1);
        double epsilon = 0.0000001;
        
        boolean oneway0to1 = (Math.abs(startingBetaValue) < epsilon && Math.abs(endingBetaValue - 1.0) < epsilon);
        boolean oneway1to0 = (Math.abs(startingBetaValue - 1.0) < epsilon && Math.abs(endingBetaValue) < epsilon);


        boolean bothways0to0 = (Math.abs(startingBetaValue) < epsilon && Math.abs(endingBetaValue) < epsilon);
        boolean bothways1to1 = (Math.abs(startingBetaValue - 1.0) < epsilon && Math.abs(endingBetaValue - 1.0) < epsilon);

        if(oneway0to1 || oneway1to0){
            //We have a oneway analysis to perform
            double result = oneWayAnalysis(valuesFromFile);
            System.out.println("Log file analysed. The log Bayes factor calculated is: ");
            System.out.println(result);


        }
        else if(bothways0to0 || bothways1to1){
            //We have a bothways analysis to perform
            //Not 100% certain of how to exactly this will be done

        }
    }

    private static void twoFileAnalysis(ArrayList<Double>[] valuesFromFile0, ArrayList<Double>[] valuesFromFile1){

    }

    private static double oneWayAnalysis(ArrayList<Double>[] valuesFromFile){
        ArrayList<Double> usefulUValues = new ArrayList<>();
        boolean betaIsChanging = true; //Assume true at first, then set to false if find it is not the case
        for (int row = 0; row < valuesFromFile[0].size(); row++){
            //For each row in the file
            if (row == 0) {
                if (valuesFromFile[0].get(row).equals(valuesFromFile[0].get(row + 1))){
                    betaIsChanging = false;
                } else {
                    //Use the current line as the first U value
                    usefulUValues.add(valuesFromFile[1].get(row));
                }
            }
            else if( ! betaIsChanging){ //Need to notice when beta starts changing
                if ((row + 1) != valuesFromFile[0].size()) { //Checking that there is an element one ahead before using it
                    if ( ! valuesFromFile[0].get(row).equals(valuesFromFile[0].get(row + 1))) {
                        betaIsChanging = true;
                        usefulUValues.add(valuesFromFile[1].get(row));
                    }
                }
            }
            else if (betaIsChanging){
                usefulUValues.add(valuesFromFile[1].get(row));
            }

        }


        //Calculate using the usefulUValues
        double Utotal = 0.0;
        for (int i = 0; i < usefulUValues.size(); i++ ){
            if (i == 0 || i == usefulUValues.size() - 1){
                Utotal = Utotal + (usefulUValues.get(i) * 0.5);
            }
            else{
                Utotal = Utotal + usefulUValues.get(i);
            }
        }

        double result = Utotal / usefulUValues.size();
        return result;
    }
}


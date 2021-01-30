package com.felixgrund.codeshovel;

import com.felixgrund.codeshovel.util.Thresholds;
import com.felixgrund.codeshovel.wrappers.StartEnvironment;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class ThresholdRunner {
    public ThresholdRunner() {
        System.out.println("ThresholdRunner::init");
    }

    private ArrayList<String> messages = new ArrayList<>();

    /**
     * Trying different permutations generates a lot of log messages.
     * Cache the key messages from ThresholdRunner so they can be
     * played back at the end of the execution.
     *
     * @param msg
     */
    public void print(String msg) {
        System.out.println("ThresholdRunner::" + msg);
        messages.add(msg);
    }

    public void doIt() throws Exception {
        print("START: " + new Date());
        print("doIt() - start");

        String result = "";
        int count = 0;

        Thresholds.resetAll();
        // 1
        evaluateConfig(count++);
        print("doIt() - CONFIG RESULT BASELINE, NO CHANGES");

//        Thresholds.resetAll();
//        Thresholds.MOST_SIM_FUNCTION.setValue(0.82f);
//        // 2
//        evaluateConfig(count++);
//
//        Thresholds.resetAll();
//        Thresholds.MOST_SIM_FUNCTION.setValue(0.85f);
//        // 3
//        evaluateConfig(count++);

        print("doIt() - ThresholdRunner::doIt() - done");

        System.out.println("****");
        System.out.println("**** Threshold Results");
        System.out.println("****");
        for (String msg : this.messages) {
            System.out.println(msg);
        }
        System.out.println("****");
        System.out.println("**** / Threshold Results");
        System.out.println("****");
        print("END: " + new Date());
    }

    private void evaluateConfig(int count) throws Exception {
        String result = run();
        count++;
        print("evalConfig() - CONFIG RESULT" + count + " complete; config: ");
        print(Thresholds.toDiffJSON());
        print("evalConfig() - CONFIG RESULT " + count + ": " + result);
    }

    public String run() throws Exception {
        System.out.println("run() - ThresholdRunner::run() - start");

        MainDynamicStubTest runner = new MainDynamicStubTest();
        List<StartEnvironment> envs = runner.selectEnvironments();

        int successCount = 0;
        int failCount = 0;
        for (StartEnvironment env : envs) {
            boolean success = runner.performComparison(env);
            if (success) {
                System.out.println("Succeeded: " + env.getEnvName());
                successCount++;
            } else {
                System.err.println("Failed: " + env.getEnvName());
                failCount++;
            }
        }
        String msg = "# success: " + successCount + "; # fail: " + failCount;
        System.out.println("ThresholdRunner::run() - done; " + msg);
        return msg;
    }

    public static void main(String[] args) {
        ThresholdRunner tr = new ThresholdRunner();
        try {
            tr.doIt();
        } catch (Exception e) {
            System.err.println("ThresholdRunner - ERROR: " + e.getMessage());
        }

    }
}
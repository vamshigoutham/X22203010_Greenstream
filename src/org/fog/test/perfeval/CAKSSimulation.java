package org.fog.test.perfeval;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.application.AppEdge;
import org.fog.application.Application;
import org.fog.entities.FogDevice;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.Tuple;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.placement.ModulePlacementMapping;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.Logger;
import org.fog.utils.TimeKeeper;
import org.apache.commons.math3.optim.MaxIter;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.linear.*;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.SimplexOptimizer;

import java.util.*;

class Job {
    int id;
    int mips;
    int duration;
    int deadline;

    Job(int id, int mips, int duration, int deadline) {
        this.id = id;
        this.mips = mips;
        this.duration = duration;
        this.deadline = deadline;
    }
}

public class CAKSSimulation extends SimEntity {

    private static final int TIME_HORIZON = 10;
    private static final List<Job> JOBS = new ArrayList<>();
    private static List<FogDevice> fogDevices = new ArrayList<>();
    private static final boolean CLOUD = false;

    public CAKSSimulation(String name) {
        super(name);
    }

    public static void main(String[] args) {
        try {
            Logger.ENABLED = true;
            Log.printLine("Starting CAKS, RRAPX, and SRRAPX Simulation...");

            CloudSim.init(1, Calendar.getInstance(), false);

            initializeJobs();
            fogDevices = createFogDevices();

            CAKSSimulation caksSim = new CAKSSimulation("CAKS_Sim");
            caksSim.schedule(caksSim.getId(), 0, 0);

            Application application = createApplication();

            ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();
            for (FogDevice device : fogDevices) {
                if (device.getName().startsWith("dc")) {
                    moduleMapping.addModuleToDevice("client", device.getName());
                    moduleMapping.addModuleToDevice("scheduler", device.getName());
                    moduleMapping.addModuleToDevice("cloud", device.getName());
                }
            }

            Controller controller = new Controller("master-controller", fogDevices, new ArrayList<>(), new ArrayList<>());
            controller.submitApplication(application,
                    (CLOUD) ? (new ModulePlacementMapping(fogDevices, application, moduleMapping))
                            : (new ModulePlacementEdgewards(fogDevices, new ArrayList<>(), new ArrayList<>(), application, moduleMapping)));

            TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());

            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            printResults(fogDevices);

            Log.printLine("CAKS, RRAPX, and SRRAPX Simulation finished!");
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("The simulation has been terminated due to an unexpected error");
        }
    }

    @Override
    public void startEntity() {
        Log.printLine(getName() + " is starting...");
        Log.printLine("Scheduling the simulation event...");
        schedule(getId(), 0, 0);
    }

    @Override
    public void processEvent(SimEvent ev) {
        Log.printLine("Processing simulation event...");

        double[][] simulatedCarbonIntensity = {
        	    {0.8, 0.3, 0.2, 0.1, 0.3, 0.4, 0.2, 0.3, 0.2, 0.1},
        	    {0.3, 0.4, 0.3, 0.2, 0.1, 0.2, 0.3, 0.4, 0.3, 0.2},
        	    {0.1, 0.2, 0.3, 0.4, 0.2, 0.3, 0.1, 0.2, 0.3, 0.4},
        	    {0.3, 0.2, 0.1, 0.4, 0.3, 0.2, 0.3, 0.2, 0.1, 0.4},
        	    {0.2, 0.1, 0.3, 0.2, 0.1, 0.3, 0.2, 0.1, 0.3, 0.2}
        	};

        double[][] simulatedResourceAvailability = {
                {4000, 4000, 4000, 4000, 4000, 4000, 4000, 4000, 4000, 4000},
                {4500, 4500, 4500, 4500, 4500, 4500, 4500, 4500, 4500, 4500},
                {8000, 8000, 8000, 8000, 8000, 8000, 8000, 8000, 8000, 8000},
                {3500, 3500, 3500, 3500, 3500, 3500, 3500, 3500, 3500, 3500},
                {3800, 3800, 3800, 3800, 3800, 3800, 3800, 3800, 3800, 3800}
        };

        double bestGRUCAKS = runCAKSSimulation(simulatedCarbonIntensity, simulatedResourceAvailability);
        double bestGRURRAPX = runRRAPXSimulation(simulatedCarbonIntensity, simulatedResourceAvailability);
        double bestGRUSRRAPX = runSRRAPXSimulation(simulatedCarbonIntensity, simulatedResourceAvailability);

        Log.printLine("CAKS Best GRU: " + bestGRUCAKS);
        Log.printLine("RRAPX Best GRU: " + bestGRURRAPX);
        Log.printLine("SRRAPX Best GRU: " + bestGRUSRRAPX);
    }

    @Override
    public void shutdownEntity() {
        Log.printLine(getName() + " is shutting down...");
    }

    private static void initializeJobs() {
        Log.printLine("Initializing jobs...");
        JOBS.add(new Job(1, 4000, 1, 5));
        JOBS.add(new Job(2, 3000, 1, 7));
        JOBS.add(new Job(3, 3500, 1, 4));
        JOBS.add(new Job(4, 3500, 1, 7));
        JOBS.add(new Job(5, 2500, 1, 4));
        JOBS.add(new Job(6, 5000, 1, 3));
        JOBS.add(new Job(7, 3800, 1, 2));
        JOBS.add(new Job(8, 3600, 1, 3));
        JOBS.add(new Job(9, 2300, 2, 7));
        JOBS.add(new Job(10, 3900, 1, 4));
    }

    private static List<FogDevice> createFogDevices() {
        List<FogDevice> fogDevices = new ArrayList<>();
        fogDevices.add(createFogDevice("dc1", 6000, 10000, 10000, 10000, 3));
        fogDevices.add(createFogDevice("dc2", 9500, 10000, 10000, 10000, 3));
        fogDevices.add(createFogDevice("dc3", 9000, 10000, 10000, 10000, 3));
        fogDevices.add(createFogDevice("dc4", 9500, 10000, 10000, 10000, 3));
        fogDevices.add(createFogDevice("dc5", 9800, 10000, 10000, 10000, 3));
        fogDevices.add(createFogDevice("cloud", 44800, 40000, 100, 10000, 0));
        return fogDevices;
    }

    private static FogDevice createFogDevice(String name, long mips, int ram, long upBw, long downBw, int level) {
        Log.printLine("Creating fog device: " + name);
        try {
            List<Pe> peList = new ArrayList<>();
            peList.add(new Pe(0, new PeProvisionerOverbooking(mips)));

            int hostId = FogUtils.generateEntityId();
            long storage = 1000000; // 1 GB
            int bw = 10000;

            PowerHost host = new PowerHost(
                    hostId,
                    new RamProvisionerSimple(ram),
                    new BwProvisionerOverbooking(bw),
                    storage,
                    peList,
                    new StreamOperatorScheduler(peList),
                    new FogLinearPowerModel(107.339, 83.4333)
            );

            List<Host> hostList = new ArrayList<>();
            hostList.add(host);

            String arch = "x86";      // system architecture
            String os = "Linux";      // operating system
            String vmm = "Xen";       // virtual machine manager
            double time_zone = 10.0;  // time zone this resource located
            double cost = 3.0;        // the cost of using processing in this resource
            double costPerMem = 0.05; // the cost of using memory in this resource
            double costPerStorage = 0.001; // the cost of using storage in this resource
            double costPerBw = 0.0;   // the cost of using bandwidth in this resource

            LinkedList<Storage> storageList = new LinkedList<>(); // we are not adding SAN devices by now

            FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
                    arch, os, vmm, host, time_zone, cost, costPerMem,
                    costPerStorage, costPerBw);

            FogDevice fogdevice = new FogDevice(name, characteristics,
                    new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 0, 0);

            fogdevice.setLevel(level);
            return fogdevice;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    private static Application createApplication() {
        Application application = new Application("CAKSApp", 1);

        // Application modules
        application.addAppModule("client", 10);
        application.addAppModule("scheduler", 10);
        application.addAppModule("cloud", 10);

        // Application edges
        application.addAppEdge("SENSOR", "client", 1000, 500, "SENSOR", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("client", "scheduler", 1000, 500, "_SENSOR", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("scheduler", "cloud", 1000, 500, "PROCESSING", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("cloud", "client", 1000, 500, "RESPONSE", Tuple.DOWN, AppEdge.MODULE);

        return application;
    }

    private double runCAKSSimulation(double[][] carbonIntensity, double[][] resourceAvailability) {
        double bestGRU = 0;
        double currentGRU = 0;
        double[][] schedulingMatrix = new double[fogDevices.size()][TIME_HORIZON];

        Log.printLine("Simulating CAKS algorithm");

        for (Job job : JOBS) {
            int[] assignment = scheduleJobCAKS(job, carbonIntensity, resourceAvailability);
            if (assignment != null) {
                int dcIndex = assignment[0];
                int startTime = assignment[1];
                Log.printLine("Job " + job.id + " assigned to " + fogDevices.get(dcIndex).getName() + " starting at time " + startTime);
                for (int t = startTime; t < startTime + job.duration; t++) {
                    schedulingMatrix[dcIndex][t] += job.mips;
                    resourceAvailability[dcIndex][t] -= job.mips; // Update resource availability
                    // Update energy consumption based on carbon intensity
                    double energyConsumed = job.mips * carbonIntensity[dcIndex][t];
                    fogDevices.get(dcIndex).setEnergyConsumption(fogDevices.get(dcIndex).getEnergyConsumption() + energyConsumed);
                }
            } else {
                Log.printLine("Job " + job.id + " could not be assigned.");
            }
        }

        currentGRU = calculateGRU(schedulingMatrix, carbonIntensity);

        if (currentGRU > bestGRU) {
            bestGRU = currentGRU;
        }

        return bestGRU;
    }

    private int[] scheduleJobCAKS(Job job, double[][] carbonIntensity, double[][] resourceAvailability) {
        double minCost = Double.MAX_VALUE;
        int[] bestAssignment = null;

        for (int dc = 0; dc < fogDevices.size() - 1; dc++) {
            for (int t = 0; t <= TIME_HORIZON - job.duration; t++) {
                if (t + job.duration > TIME_HORIZON || t + job.duration > job.deadline) {
                    continue;
                }

                boolean feasible = true;
                double cost = 0;
                for (int d = t; d < t + job.duration; d++) {
                    if (d >= carbonIntensity[dc].length || d >= resourceAvailability[dc].length) {
                        feasible = false;
                        break;
                    }
                    if (resourceAvailability[dc][d] < job.mips) {
                        feasible = false;
                        break;
                    }
                    cost += carbonIntensity[dc][d];
                }

                if (feasible && cost < minCost) {
                    minCost = cost;
                    bestAssignment = new int[]{dc, t};
                }
            }
        }

        return bestAssignment;
    }

    private double runRRAPXSimulation(double[][] carbonIntensity, double[][] resourceAvailability) {
        double bestGRU = 0;
        double[][] schedulingMatrix = new double[fogDevices.size()][TIME_HORIZON];

        double[][] fractionalSolution = solveLPRelaxation(JOBS, carbonIntensity, resourceAvailability);
        Log.printLine("Simulating RRAPX algorithm");

        for (Job job : JOBS) {
            int[] assignment = scheduleJobRRAPX(job, fractionalSolution, carbonIntensity, resourceAvailability);
            if (assignment != null) {
                int dcIndex = assignment[0];
                int startTime = assignment[1];
                Log.printLine("Job " + job.id + " assigned to dc" + (dcIndex + 1) + " starting at time " + startTime);
                for (int t = startTime; t < (startTime + job.duration) - 1; t++) {
                    schedulingMatrix[dcIndex][t] += job.mips;
                    resourceAvailability[dcIndex][t] -= job.mips; // Update resource availability
                    // Update energy consumption based on carbon intensity
                    double energyConsumed = job.mips * carbonIntensity[dcIndex][t];
                    fogDevices.get(dcIndex).setEnergyConsumption(fogDevices.get(dcIndex).getEnergyConsumption() + energyConsumed);
                }
            } else {
                Log.printLine("Job " + job.id + " could not be assigned.");
            }
        }

        bestGRU = calculateGRU(schedulingMatrix, carbonIntensity);

        return bestGRU;
    }

    private int[] scheduleJobRRAPX(Job job, double[][] fractionalSolution, double[][] carbonIntensity, double[][] resourceAvailability) {
        int M = fogDevices.size();
        int T = TIME_HORIZON;

        for (int dc = 0; dc < M - 1; dc++) {
            for (int t = 0; t < T; t++) {
                if (fractionalSolution[job.id - 1][dc * T + t] > 0) {
                    boolean feasible = true;
                    for (int d = t; d < (t + job.duration) - 1; d++) {
                        if (resourceAvailability[dc][d] < job.mips) {
                            feasible = false;
                            break;
                        }
                    }
                    if (feasible) {
                        return new int[]{dc, t};
                    }
                }
            }
        }
        return null;
    }

    private double runSRRAPXSimulation(double[][] carbonIntensity, double[][] resourceAvailability) {
        double bestGRU = 0;
        double[][] bestSchedulingMatrix = new double[fogDevices.size()][TIME_HORIZON];
        int S = 1;

        double[][] fractionalSolution = solveLPRelaxation(JOBS, carbonIntensity, resourceAvailability);
        Log.printLine("Simulating SRRAPX algorithm");
        for (int s = 0; s < S; s++) {

            double[][] schedulingMatrix = new double[fogDevices.size()][TIME_HORIZON];
            int failedJobs = 0;

            for (Job job : JOBS) {
                int[] assignment = scheduleJobSRRAPX(job, fractionalSolution, carbonIntensity, resourceAvailability);
                if (assignment != null) {
                    int dcIndex = assignment[0];
                    int startTime = assignment[1];
                    Log.printLine("Job " + job.id + " assigned to dc" + (dcIndex + 1) + " starting at time " + startTime);
                    boolean jobScheduled = true;
                    for (int t = startTime; t < startTime + job.duration; t++) {
                        if (schedulingMatrix[dcIndex][t] + job.mips > resourceAvailability[dcIndex][t]) {
                            failedJobs++;
                            jobScheduled = false;
                            Log.printLine("Job " + job.id + " could not be scheduled at dc" + (dcIndex + 1) + " due to insufficient resources at time " + t);
                            break;
                        } else {
                            schedulingMatrix[dcIndex][t] += job.mips;
                            resourceAvailability[dcIndex][t] -= job.mips; // Updating resource availability in this line
                            // Update energy consumption based on carbon intensity
                            double energyConsumed = job.mips * carbonIntensity[dcIndex][t];
                            fogDevices.get(dcIndex).setEnergyConsumption(fogDevices.get(dcIndex).getEnergyConsumption() + energyConsumed);
                        }
                    }
                } else {
                    failedJobs++;
                    Log.printLine("Job " + job.id + " could not be assigned.");
                }
            }

            double gru = calculateGRU(schedulingMatrix, carbonIntensity);
            bestGRU = gru;
            if (failedJobs == 0 && gru > bestGRU) {
                bestGRU = gru;
                bestSchedulingMatrix = schedulingMatrix;
            }
        }

        return bestGRU;
    }

    private static int[] scheduleJobSRRAPX(Job job, double[][] fractionalSolution, double[][] carbonIntensity, double[][] resourceAvailability) {
        int M = fogDevices.size();
        int T = TIME_HORIZON;

        for (int dc = 0; dc < M; dc++) {
            for (int t = 0; t < T; t++) {
                if (fractionalSolution[job.id - 1][dc * T + t] > 0) {
                    boolean feasible = true;
                    if (t + job.duration > T) {
                        feasible = false;
                        break;
                    }
                    for (int d = t; d < t + job.duration; d++) {
                        if (resourceAvailability[dc][d] < job.mips) {
                            feasible = false;
                            break;
                        }
                    }
                    if (feasible) {
                        return new int[]{dc, t};
                    }
                }
            }
        }

        return null;
    }

    private double calculateGRU(double[][] schedulingMatrix, double[][] carbonIntensity) {
        double gru = 0;
        for (int i = 0; i < fogDevices.size(); i++) {
            for (int t = 0; t < TIME_HORIZON; t++) {
                if (schedulingMatrix[i][t] > 0) {
                    gru += schedulingMatrix[i][t] / carbonIntensity[i][t];
                    // Update energy consumption based on carbon intensity
                    double energyConsumed = schedulingMatrix[i][t] * carbonIntensity[i][t];
                    fogDevices.get(i).setEnergyConsumption(fogDevices.get(i).getEnergyConsumption() + energyConsumed);
                }
            }
        }
        return gru;
    }

    private double[][] solveLPRelaxation(List<Job> jobs, double[][] carbonIntensity, double[][] resourceAvailability) {
        int N = jobs.size();
        int M = fogDevices.size();
        int T = TIME_HORIZON;

        double[][] fractionalSolution = new double[N][M * T];

        double[] objectiveFunction = new double[N * M * T];
        int index = 0;
        for (Job job : jobs) {
            for (int dc = 0; dc < M - 1; dc++) {
                for (int t = 0; t < T; t++) {
                    objectiveFunction[index++] = carbonIntensity[dc][t];
                }
            }
        }
        LinearObjectiveFunction objective = new LinearObjectiveFunction(objectiveFunction, 0);

        List<LinearConstraint> constraints = new ArrayList<>();

        for (int dc = 0; dc < M - 1; dc++) {
            for (int t = 0; t < T; t++) {
                double[] coefficients = new double[N * M * T];
                index = 0;
                for (Job job : jobs) {
                    for (int d = 0; d < M; d++) {
                        for (int ti = 0; ti < T; ti++) {
                            if (dc == d && t == ti) {
                                coefficients[index] = job.mips;
                            }
                            index++;
                        }
                    }
                }
                constraints.add(new LinearConstraint(coefficients, Relationship.LEQ, resourceAvailability[dc][t]));
            }
        }

        for (Job job : jobs) {
            double[] coefficients = new double[N * M * T];
            index = 0;
            for (int j = 0; j < N; j++) {
                for (int dc = 0; dc < M; dc++) {
                    for (int t = 0; t < T; t++) {
                        if (job.id - 1 == j) {
                            coefficients[index] = 1;
                        }
                        index++;
                    }
                }
            }
            constraints.add(new LinearConstraint(coefficients, Relationship.EQ, 1));
        }

        SimplexSolver solver = new SimplexSolver();
        PointValuePair solution = solver.optimize(new MaxIter(1000), objective, new LinearConstraintSet(constraints), GoalType.MINIMIZE, new NonNegativeConstraint(true));

        if (solution != null) {
            double[] solutionPoint = solution.getPoint();
            index = 0;
            for (int i = 0; i < N; i++) {
                for (int j = 0; j < M * T; j++) {
                    fractionalSolution[i][j] = solutionPoint[index++];
                }
            }
        }

        return fractionalSolution;
    }

    private static void printResults(List<FogDevice> fogDevices) {
        Log.printLine("=========================================");
        Log.printLine("============== RESULTS ==================");
        Log.printLine("=========================================");
        Log.printLine("EXECUTION TIME : " + CloudSim.clock());
        Log.printLine("=========================================");
        Log.printLine("APPLICATION LOOP DELAYS");
        Log.printLine("=========================================");
        Log.printLine("=========================================");
        Log.printLine("TUPLE CPU EXECUTION DELAY");
        Log.printLine("=========================================");
        for (FogDevice device : fogDevices) {
            Log.printLine(device.getName() + " : Energy Consumed = " + device.getEnergyConsumption());
        }
    }
}

package org.fog.test.perfeval;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.entities.Actuator;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.Sensor;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.placement.ModulePlacementMapping;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
import org.fog.utils.distribution.DeterministicDistribution;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.power.PowerHost;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.overbooking.BwProvisionerOverbooking;
import org.cloudbus.cloudsim.sdn.overbooking.PeProvisionerOverbooking;
import org.fog.policy.AppModuleAllocationPolicy;
import org.fog.scheduler.StreamOperatorScheduler;
import org.fog.entities.FogDeviceCharacteristics;
import org.fog.entities.Tuple;

public class smartCitiesSector {

    static List<FogDevice> fogDevices = new ArrayList<>();
    static List<Sensor> sensors = new ArrayList<>();
    static List<Actuator> actuators = new ArrayList<>();
    static int numOfZones = 5;
    private static boolean CLOUD = false;

    public static void main(String[] args) {
        try {
            Log.disable();
            int num_user = 1;
            Calendar calendar = Calendar.getInstance();
            CloudSim.init(num_user, calendar, false);

            String appId = "smartCitiesMonitoring";
            FogBroker broker = new FogBroker("broker");

            Application application = createApplication(appId, broker.getId());

            createFogDevices(broker.getId(), appId);

            ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();
            for (FogDevice device : fogDevices) {
                if (device.getName().startsWith("edge")) {
                    moduleMapping.addModuleToDevice("air_quality_module", device.getName());
                    moduleMapping.addModuleToDevice("camera_module", device.getName());
                    moduleMapping.addModuleToDevice("noise_level_module", device.getName());
                    moduleMapping.addModuleToDevice("traffic_density_module", device.getName());
                    moduleMapping.addModuleToDevice("weather_module", device.getName());
                }
            }
            if (CLOUD) {
                moduleMapping.addModuleToDevice("air_quality_module", "cloud");
                moduleMapping.addModuleToDevice("camera_module", "cloud");
                moduleMapping.addModuleToDevice("noise_level_module", "cloud");
                moduleMapping.addModuleToDevice("traffic_density_module", "cloud");
                moduleMapping.addModuleToDevice("weather_module", "cloud");
                moduleMapping.addModuleToDevice("data_analyzer", "cloud");
            }
            moduleMapping.addModuleToDevice("data_analyzer", "cloud");
            Controller controller = new Controller("master-controller", fogDevices, sensors, actuators);
            controller.submitApplication(application,
                    (CLOUD) ? (new ModulePlacementMapping(fogDevices, application, moduleMapping))
                            : (new ModulePlacementEdgewards(fogDevices, sensors, actuators, application, moduleMapping)));

            TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());
            CloudSim.startSimulation();
            CloudSim.stopSimulation();

            Log.printLine("Smart Cities Monitoring Simulation finished!");
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted errors happened");
        }
    }

    private static void createFogDevices(int userId, String appId) throws Exception {
        FogDevice cloud = createFogDevice("cloud", 54800, 50000, 200, 20000, 0, 0.04, 18 * 109, 18 * 89.25);
        cloud.setParentId(-1);
        fogDevices.add(cloud);

        FogDevice proxy = createFogDevice("proxy-server", 3800, 5000, 11000, 20000, 1, 0.0, 107.339, 89.4333);
        proxy.setParentId(cloud.getId());
        proxy.setUplinkLatency(100);
        fogDevices.add(proxy);

        // Create edge devices for each zone
        for (int i = 1; i <= numOfZones; i++) {
            FogDevice edgeDevice = createFogDevice("edge-device-" + i, 4800, 3000, 2000, 20000, 2, 0.0, 107.339, 89.4333);
            edgeDevice.setParentId(proxy.getId());
            edgeDevice.setUplinkLatency(2);
            fogDevices.add(edgeDevice);
            linkSensorsAndActuatorsToFogDevices(userId, appId, edgeDevice);
        }
    }

    private static void linkSensorsAndActuatorsToFogDevices(int userId, String appId, FogDevice edgeDevice) {
        Sensor airQualitySensor = new Sensor("airQualitySensor-" + edgeDevice.getName(), "AIR_QUALITY", userId, appId, new DeterministicDistribution(5)); // Generates data every 5 seconds
        airQualitySensor.setGatewayDeviceId(edgeDevice.getId());
        sensors.add(airQualitySensor);

        Sensor cameraSensor = new Sensor("cameraSensor-" + edgeDevice.getName(), "CAMERA", userId, appId, new DeterministicDistribution(6)); // Generates data every 6 seconds
        cameraSensor.setGatewayDeviceId(edgeDevice.getId());
        sensors.add(cameraSensor);

        Sensor noiseLevelSensor = new Sensor("noiseLevelSensor-" + edgeDevice.getName(), "NOISE_LEVEL", userId, appId, new DeterministicDistribution(4)); // Generates data every 4 seconds
        noiseLevelSensor.setGatewayDeviceId(edgeDevice.getId());
        sensors.add(noiseLevelSensor);

        Sensor trafficDensitySensor = new Sensor("trafficDensitySensor-" + edgeDevice.getName(), "TRAFFIC_DENSITY", userId, appId, new DeterministicDistribution(6)); // Generates data every 6 seconds
        trafficDensitySensor.setGatewayDeviceId(edgeDevice.getId());
        sensors.add(trafficDensitySensor);

        Sensor weatherSensor = new Sensor("weatherSensor-" + edgeDevice.getName(), "WEATHER", userId, appId, new DeterministicDistribution(5)); // Generates data every 5 seconds
        weatherSensor.setGatewayDeviceId(edgeDevice.getId());
        sensors.add(weatherSensor);

        Actuator alarmActuator = new Actuator("alarmActuator-" + edgeDevice.getName(), userId, appId, "ALARM");
        alarmActuator.setGatewayDeviceId(edgeDevice.getId());
        actuators.add(alarmActuator);
    }

    private static FogDevice createFogDevice(String nodeName, long mips,
            int ram, long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower) {

        List<Pe> peList = new ArrayList<Pe>();

        peList.add(new Pe(0, new PeProvisionerOverbooking(mips))); // need to store Pe id and MIPS Rating

        int hostId = FogUtils.generateEntityId();
        long storage = 1000000; // host storage
        int bw = 10000;

        PowerHost host = new PowerHost(
                hostId,
                new RamProvisionerSimple(ram),
                new BwProvisionerOverbooking(bw),
                storage,
                peList,
                new StreamOperatorScheduler(peList),
                new FogLinearPowerModel(busyPower, idlePower)
        );

        List<Host> hostList = new ArrayList<Host>();
        hostList.add(host);

        String arch = "x86"; // system architecture
        String os = "Linux"; // operating system
        String vmm = "Xen";
        double time_zone = 10.0; // time zone this resource located
        double cost = 3.0; // the cost of using processing in this resource
        double costPerMem = 0.05; // the cost of using memory in this resource
        double costPerStorage = 0.001; // the cost of using storage in this
        // resource
        double costPerBw = 0.0; // the cost of using bw in this resource
        LinkedList<Storage> storageList = new LinkedList<Storage>();

        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
                arch, os, vmm, host, time_zone, cost, costPerMem,
                costPerStorage, costPerBw);

        FogDevice fogdevice = null;
        try {
            fogdevice = new FogDevice(nodeName, characteristics,
                    new AppModuleAllocationPolicy(hostList), storageList, 10, upBw, downBw, 0, ratePerMips);
        } catch (Exception e) {
            e.printStackTrace();
        }

        fogdevice.setLevel(level);
        return fogdevice;
    }

    private static Application createApplication(String appId, int userId) {
        Application application = Application.createApplication(appId, userId); // Basic app creation
        application.addAppModule("air_quality_module", 20); // Processes air quality data
        application.addAppModule("camera_module", 20); // Processes camera data
        application.addAppModule("noise_level_module", 20); // Processes noise level data
        application.addAppModule("traffic_density_module", 20); // Processes traffic density data
        application.addAppModule("weather_module", 20); // Processes weather data
        application.addAppModule("data_analyzer", 0); // Analyzes data from all modules

        // Define edges from sensors to modules
        application.addAppEdge("AIR_QUALITY", "air_quality_module", 1000, 2000, "AIR_QUALITY", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("CAMERA", "camera_module", 1000, 500, "CAMERA", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("NOISE_LEVEL", "noise_level_module", 1000, 500, "NOISE_LEVEL", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("TRAFFIC_DENSITY", "traffic_density_module", 1000, 500, "TRAFFIC_DENSITY", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("WEATHER", "weather_module", 1000, 500, "WEATHER", Tuple.UP, AppEdge.SENSOR);

        application.addAppEdge("air_quality_module", "data_analyzer", 500, 100, "AIR_QUALITY_ALERT", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("camera_module", "data_analyzer", 500, 100, "CAMERA_ALERT", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("noise_level_module", "data_analyzer", 500, 100, "NOISE_LEVEL_ALERT", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("traffic_density_module", "data_analyzer", 500, 100, "TRAFFIC_DENSITY_ALERT", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("weather_module", "data_analyzer", 500, 100, "WEATHER_ALERT", Tuple.UP, AppEdge.MODULE);

        application.addAppEdge("data_analyzer", "ALARM", 100, 50, "ALARM_SIGNAL", Tuple.DOWN, AppEdge.ACTUATOR);

        // Define tuple mappings to specify how data is processed within modules
        application.addTupleMapping("air_quality_module", "AIR_QUALITY", "AIR_QUALITY_ALERT", new FractionalSelectivity(0.7));
        application.addTupleMapping("camera_module", "CAMERA", "CAMERA_ALERT", new FractionalSelectivity(1.0));
        application.addTupleMapping("noise_level_module", "NOISE_LEVEL", "NOISE_LEVEL_ALERT", new FractionalSelectivity(0.7));
        application.addTupleMapping("traffic_density_module", "TRAFFIC_DENSITY", "TRAFFIC_DENSITY_ALERT", new FractionalSelectivity(0.5));
        application.addTupleMapping("weather_module", "WEATHER", "WEATHER_ALERT", new FractionalSelectivity(0.3));

        // Define loops to monitor latency-sensitive operations
        final AppLoop loop1 = new AppLoop(new ArrayList<String>() {{
            add("air_quality_module");
            add("data_analyzer");
        }});
        final AppLoop loop2 = new AppLoop(new ArrayList<String>() {{
            add("camera_module");
            add("data_analyzer");
        }});
        final AppLoop loop3 = new AppLoop(new ArrayList<String>() {{
            add("noise_level_module");
            add("data_analyzer");
        }});
        final AppLoop loop4 = new AppLoop(new ArrayList<String>() {{
            add("traffic_density_module");
            add("data_analyzer");
        }});
        final AppLoop loop5 = new AppLoop(new ArrayList<String>() {{
            add("weather_module");
            add("data_analyzer");
        }});

        List<AppLoop> loops = new ArrayList<AppLoop>() {{
            add(loop1);
            add(loop2);
            add(loop3);
            add(loop4);
            add(loop5);
        }};

        application.setLoops(loops);

        return application;
    }
}

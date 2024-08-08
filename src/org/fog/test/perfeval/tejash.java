package org.fog.test.perfeval;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.entities.Actuator;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.entities.Sensor;
import org.fog.entities.Tuple;
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

//file and class name should be same and remove those instructions before submitting
public class tejash {
    static List<FogDevice> fogDevices = new ArrayList<>();
    static List<Sensor> sensors = new ArrayList<>();
    static List<Actuator> actuators = new ArrayList<>();
    static int numOfAreas = 1;
    private static boolean CLOUD = false;

    public static void main(String[] args) {
        try {
            Log.disable();
            int num_user = 1;
            Calendar calendar = Calendar.getInstance();
            CloudSim.init(num_user, calendar, false);

            //change appId as per the domain
            String appId = "Clothing Manufacturing";
            FogBroker broker = new FogBroker("broker");

            Application application = createApplication(appId, broker.getId());

            createFogDevices(broker.getId(), appId);

            ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();
            for (FogDevice device : fogDevices) {
                if (device.getName().startsWith("edge")) {
                	//update those module names over here as well
                    moduleMapping.addModuleToDevice("gyroscope_module", device.getName());
                    moduleMapping.addModuleToDevice("accelerometer_module", device.getName());
                    moduleMapping.addModuleToDevice("pressure_sensor_module", device.getName());
                    moduleMapping.addModuleToDevice("temperature_module", device.getName());
                    moduleMapping.addModuleToDevice("humidity_module", device.getName());
                }
            }

            if (CLOUD) {
            	//update those module names over here as well
                moduleMapping.addModuleToDevice("gyroscope_module", "cloud");
                moduleMapping.addModuleToDevice("accelerometer_module", "cloud");
                moduleMapping.addModuleToDevice("pressure_sensor_module", "cloud");
                moduleMapping.addModuleToDevice("temperature_module", "cloud");
                moduleMapping.addModuleToDevice("humidity_module", "cloud");
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
            
            //change text below as per domain
            Log.printLine("Clothing Manufacturing Sensor Integration Simulation finished!");
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted errors happened");
        }
    }

    private static void createFogDevices(int userId, String appId) throws Exception {
        FogDevice cloud = createFogDevice("cloud", 44800, 40000, 100, 10000, 0, 0.01, 16 * 103, 16 * 83.25);
        cloud.setParentId(-1);
        fogDevices.add(cloud);

        FogDevice proxy = createFogDevice("proxy-server", 2800, 4000, 10000, 10000, 1, 0.0, 107.339, 83.4333);
        proxy.setParentId(cloud.getId());
        proxy.setUplinkLatency(100);
        fogDevices.add(proxy);

        FogDevice edgeDevice = createFogDevice("edge-device", 2800, 4000, 1000, 10000, 2, 0.0, 107.339, 83.4333);
        edgeDevice.setParentId(proxy.getId());
        edgeDevice.setUplinkLatency(2);
        fogDevices.add(edgeDevice);
        linkSensorsAndActuatorsToFogDevices(userId, appId);
    }

    //make changes over here for all sensors and replace the name with your sensors name, dont change anything else
    private static void linkSensorsAndActuatorsToFogDevices(int userId, String appId) {
        FogDevice edgeDevice = fogDevices.get(2);
        Sensor gyroscopeSensor = new Sensor("gyroscopeSensor", "GYROSCOPE", userId, appId, new DeterministicDistribution(5));
        gyroscopeSensor.setGatewayDeviceId(edgeDevice.getId());
        sensors.add(gyroscopeSensor);

        Sensor accelerometerSensor = new Sensor("accelerometerSensor", "ACCELEROMETER", userId, appId, new DeterministicDistribution(6));
        accelerometerSensor.setGatewayDeviceId(edgeDevice.getId());
        sensors.add(accelerometerSensor);

        Sensor pressureSensor = new Sensor("pressureSensor", "PRESSURE", userId, appId, new DeterministicDistribution(7));
        pressureSensor.setGatewayDeviceId(edgeDevice.getId());
        sensors.add(pressureSensor);

        Sensor temperatureSensor = new Sensor("temperatureSensor", "TEMPERATURE", userId, appId, new DeterministicDistribution(4));
        temperatureSensor.setGatewayDeviceId(edgeDevice.getId());
        sensors.add(temperatureSensor);

        Sensor humiditySensor = new Sensor("humiditySensor", "HUMIDITY", userId, appId, new DeterministicDistribution(5));
        humiditySensor.setGatewayDeviceId(edgeDevice.getId());
        sensors.add(humiditySensor);

        Actuator controlActuator = new Actuator("controlActuator", userId, appId, "CONTROL");
        controlActuator.setGatewayDeviceId(edgeDevice.getId());
        actuators.add(controlActuator);
    }

    private static FogDevice createFogDevice(String nodeName, long mips,
                                             int ram, long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower) {

        List<Pe> peList = new ArrayList<Pe>();
        peList.add(new Pe(0, new PeProvisionerOverbooking(mips)));
        int hostId = FogUtils.generateEntityId();
        long storage = 1000000; 
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

        String arch = "x86"; 
        String os = "Linux"; 
        String vmm = "Xen";
        double time_zone = 10.0;
        double cost = 3.0;
        double costPerMem = 0.05;
        double costPerStorage = 0.001;
        double costPerBw = 0.0;
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
        Application application = Application.createApplication(appId, userId);
        //change the module name based on sensors
        application.addAppModule("gyroscope_module", 10);
        application.addAppModule("accelerometer_module", 10);
        application.addAppModule("pressure_sensor_module", 10);
        application.addAppModule("temperature_module", 10);
        application.addAppModule("humidity_module", 10);
        //this module is for alarm, you can keep it same or change it but make sure to change it everywhere
        application.addAppModule("data_analyzer", 10);

        //make changes over here with updated module and sensor name
        application.addAppEdge("GYROSCOPE", "gyroscope_module", 1000, 2000, "GYROSCOPE", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("ACCELEROMETER", "accelerometer_module", 2000, 1000, "ACCELEROMETER", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("PRESSURE", "pressure_sensor_module", 1000, 500, "PRESSURE", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("TEMPERATURE", "temperature_module", 1000, 500, "TEMPERATURE", Tuple.UP, AppEdge.SENSOR);
        application.addAppEdge("HUMIDITY", "humidity_module", 1000, 500, "HUMIDITY", Tuple.UP, AppEdge.SENSOR);

        
        //make changes over here with updated modules and sensors name as well as change name for alerts
        application.addAppEdge("gyroscope_module", "data_analyzer", 500, 100, "GYROSCOPE_ALERT", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("accelerometer_module", "data_analyzer", 500, 100, "ACCELEROMETER_ALERT", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("pressure_sensor_module", "data_analyzer", 500, 100, "PRESSURE_ALERT", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("temperature_module", "data_analyzer", 500, 100, "TEMP_ALERT", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("humidity_module", "data_analyzer", 500, 100, "HUMIDITY_ALERT", Tuple.UP, AppEdge.MODULE);

        //if you change the name for this, change over here as well
        application.addAppEdge("data_analyzer", "CONTROL", 100, 50, "CONTROL_SIGNAL", Tuple.DOWN, AppEdge.ACTUATOR);

        //change module names over here along with sensors and alert names
        application.addTupleMapping("gyroscope_module", "GYROSCOPE", "GYROSCOPE_ALERT", new FractionalSelectivity(0.7));
        application.addTupleMapping("accelerometer_module", "ACCELEROMETER", "ACCELEROMETER_ALERT", new FractionalSelectivity(0.7));
        application.addTupleMapping("pressure_sensor_module", "PRESSURE", "PRESSURE_ALERT", new FractionalSelectivity(0.5));
        application.addTupleMapping("temperature_module", "TEMPERATURE", "TEMP_ALERT", new FractionalSelectivity(0.3));
        application.addTupleMapping("humidity_module", "HUMIDITY", "HUMIDITY_ALERT", new FractionalSelectivity(0.5));

        List<AppLoop> loops = new ArrayList<AppLoop>();
        //change modules names over here as well
        loops.add(new AppLoop(new ArrayList<String>(){{add("gyroscope_module");add("data_analyzer");}}));
        loops.add(new AppLoop(new ArrayList<String>(){{add("accelerometer_module");add("data_analyzer");}}));
        loops.add(new AppLoop(new ArrayList<String>(){{add("pressure_sensor_module");add("data_analyzer");}}));
        loops.add(new AppLoop(new ArrayList<String>(){{add("temperature_module");add("data_analyzer");}}));
        loops.add(new AppLoop(new ArrayList<String>(){{add("humidity_module");add("data_analyzer");}}));

        application.setLoops(loops);

        return application;
    }
}
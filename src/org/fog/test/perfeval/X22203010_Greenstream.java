package org.fog.test.perfeval;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;
import java.text.SimpleDateFormat;
import java.util.Date;

import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.fog.application.Application;
import org.fog.application.selectivity.FractionalSelectivity;
import org.fog.application.AppEdge;
import org.fog.application.AppLoop;
import org.fog.entities.FogBroker;
import org.fog.entities.FogDevice;
import org.fog.placement.Controller;
import org.fog.placement.ModuleMapping;
import org.fog.placement.ModulePlacementEdgewards;
import org.fog.utils.FogLinearPowerModel;
import org.fog.utils.FogUtils;
import org.fog.utils.TimeKeeper;
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
import org.json.JSONArray;
import org.json.JSONObject;

public class X22203010_Greenstream {

    static List<FogDevice> fogDevices = new ArrayList<>();
    private static final Random RANDOM = new Random();

    public static void main(String[] args) {
        try {
            Log.enable();
            int num_user = 1;
            Calendar calendar = Calendar.getInstance();
            CloudSim.init(num_user, calendar, false);

            String appId = "Greenstream";
            FogBroker broker = new FogBroker("broker");

            Application application = createApplication(appId, broker.getId());

            createFogDevices(broker.getId(), appId);

            ModuleMapping moduleMapping = ModuleMapping.createModuleMapping();
            for (FogDevice device : fogDevices) {
                moduleMapping.addModuleToDevice("data_analyzer", device.getName());
            }

            Controller controller = new Controller("master-controller", fogDevices, new ArrayList<>(), new ArrayList<>());
            controller.submitApplication(application, new ModulePlacementEdgewards(fogDevices, new ArrayList<>(), new ArrayList<>(), application, moduleMapping));

            TimeKeeper.getInstance().setSimulationStartTime(Calendar.getInstance().getTimeInMillis());

            System.out.println("Starting API call...");
            callAreraApiDynamically(fogDevices);
            System.out.println("API call completed.");
            CloudSim.startSimulation();

            // Adding a short delay to ensure API response is processed
            Thread.sleep(5000);

            CloudSim.stopSimulation();

            Log.printLine("Retail_management Simulation finished!");
        } catch (Exception e) {
            e.printStackTrace();
            Log.printLine("Unwanted errors happened");
        }
    }

    private static void createFogDevices(int userId, String appId) throws Exception {
        FogDevice cloud = createFogDevice("cloud", 44800, 40000, 100, 10000, 0, 0.01, 1600, 1300);
        cloud.setParentId(-1);
        fogDevices.add(cloud);
        cloud.setUplinkLatency(100);

        FogDevice proxy = createFogDevice("proxy-server", 2800, 4000, 10000, 10000, 1, 0.0, 110, 90);
        proxy.setParentId(cloud.getId());
        fogDevices.add(proxy);
        proxy.setUplinkLatency(10);

        for (int i = 1; i <= 4; i++) {
            String nodeName = "dc" + i;
            FogDevice device = createFogDevice(nodeName, 2800, 4000, 1000, 10000, 2, 0.0, 100 + i * 10, 70 + i * 10); // Different power consumption values
            fogDevices.add(device);
            device.setUplinkLatency(5);
            device.setParentId(proxy.getId());
        }
    }

    private static FogDevice createFogDevice(String nodeName, long mips, int ram, long upBw, long downBw, int level, double ratePerMips, double busyPower, double idlePower) {
        List<Pe> peList = new ArrayList<>();

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

        List<Host> hostList = new ArrayList<>();
        hostList.add(host);

        String arch = "x86";
        String os = "Linux";
        String vmm = "Xen";
        double time_zone = 10.0;
        double cost = 3.0;
        double costPerMem = 0.05;
        double costPerStorage = 0.001;
        double costPerBw = 0.0;
        LinkedList<Storage> storageList = new LinkedList<>();

        FogDeviceCharacteristics characteristics = new FogDeviceCharacteristics(
                arch, os, vmm, host, time_zone, cost, costPerMem,
                costPerStorage, costPerBw
        );

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
        application.addAppModule("data_analyzer", 10);
        
        application.addAppEdge("data_analyzer", "data_analyzer", 1000, 500, "RAW_DATA", Tuple.UP, AppEdge.MODULE);
        application.addAppEdge("data_analyzer", "data_analyzer", 500, 1000, "PROCESSED_DATA", Tuple.DOWN, AppEdge.MODULE);
        
        application.addTupleMapping("data_analyzer", "RAW_DATA", "PROCESSED_DATA", new FractionalSelectivity(1.0));

        // Define application loops (e.g., sensor -> module -> actuator)
        final AppLoop loop1 = new AppLoop(new ArrayList<String>() {{
            add("data_analyzer");
        }});
        List<AppLoop> loops = new ArrayList<AppLoop>() {{
            add(loop1);
        }};

        application.setLoops(loops);

        return application;
    }

    private static void callAreraApiDynamically(List<FogDevice> fogDevices) {
        try (CloseableHttpClient httpClient = HttpClients.createDefault()) {
            System.out.println("Preparing API request...");
            HttpPost request = new HttpPost("http://greenstreamarera.us-east-1.elasticbeanstalk.com/arera/route");
            request.addHeader("Content-Type", "application/json");

            // Generate a dynamic timestamp and urgency
            String timestamp = getCurrentTimeString();
            int urgency = RANDOM.nextInt(5) + 1; // Random urgency between 1 and 5

            // Construct JSON payload
            JSONObject payload = new JSONObject();
            payload.put("urgency", urgency);
            payload.put("timestamp", timestamp);

            JSONArray dataCenters = new JSONArray();
            for (FogDevice device : fogDevices) {
                if (!device.getName().equals("cloud") && !device.getName().equals("proxy-server")) {
                    JSONObject dataCenter = new JSONObject();
                    dataCenter.put("id", device.getName());
                    dataCenter.put("RE", RANDOM.nextInt(101)); // Random RE between 0 and 100
                    dataCenter.put("CL", RANDOM.nextInt(10001)); // Random CL between 0 and 10000
                    dataCenter.put("C", 10000 + RANDOM.nextInt(10001)); // Random C between 10000 and 20000
                    dataCenter.put("D", RANDOM.nextInt(71)); // Random D between 0 and 70
                    dataCenters.put(dataCenter);
                }
            }
            payload.put("dataCenters", dataCenters);

            StringEntity entity = new StringEntity(payload.toString());
            request.setEntity(entity);

            System.out.println("Executing API request...");
            try (CloseableHttpResponse response = httpClient.execute(request)) {
                String responseString = EntityUtils.toString(response.getEntity());
                JSONObject jsonResponse = new JSONObject(responseString);
                System.out.println("Response from ARERA API: " + jsonResponse.toString(4)); // Pretty-print JSON response

                // Show message that data has been sent to the selected data center
                System.out.println("Data sent to the selected data center.");
            }
        } catch (Exception e) {
            System.out.println("Exception during API call: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static String getCurrentTimeString() {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ");
        return sdf.format(new Date());
    }
}

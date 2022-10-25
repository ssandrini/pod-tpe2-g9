package ar.edu.itba.pod.client;

import ar.edu.itba.pod.models.*;
import com.hazelcast.client.HazelcastClient;
import com.hazelcast.client.config.ClientConfig;
import com.hazelcast.client.config.ClientNetworkConfig;
import com.hazelcast.config.GroupConfig;
import com.hazelcast.core.HazelcastInstance;
import com.hazelcast.core.IList;


import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Stream;

public final class QueryUtils {

    public static final String SENSORS_FILE_NAME = "sensors.csv";
    public static final String READINGS_FILE_NAME = "readings.csv";

    private QueryUtils() {

    }

    public static String parseParameter(String[] args, String requestedParam) {
        return Stream.of(args).filter(arg -> arg.contains(requestedParam))
                .map(arg -> arg.substring(arg.indexOf("=") + 1))
                .findFirst().orElseThrow(() -> new IllegalArgumentException(
                        "Use: " + requestedParam + "=<value>")
                );
    }

    public static HazelcastInstance getHazelClientInstance(String[] args) {
        String name = "g9", pass = "g9-pass";

        ClientConfig clientConfig = new ClientConfig();

        GroupConfig groupConfig = new GroupConfig().setName(name).setPassword(pass);
        clientConfig.setGroupConfig(groupConfig);

        String[] servers = parseParameter(args, "-Daddresses").split(";");

        ClientNetworkConfig clientNetworkConfig = new ClientNetworkConfig();
        clientNetworkConfig.addAddress(servers);
        clientConfig.setNetworkConfig(clientNetworkConfig);

        return HazelcastClient.newHazelcastClient(clientConfig);
    }

    public static void logWithTimeStamp(FileWriter logWriter, String message) throws IOException {
        String timestamp = (new SimpleDateFormat("dd/MM/yyyy hh:mm:ss:SSSS")).format(new Date());
        logWriter.write(timestamp + " - " + message + "\n");
    }

    public static void loadQuery1ReadingsFromCSV(String[] args, HazelcastInstance hz, FileWriter timestampWriter) throws IOException {
        String dir = beginCSVLoad(args, timestampWriter);
        List<String> sensorLines = prepareCSVLoad(SENSORS_FILE_NAME, dir);

        Map<Long, ActiveSensor> sensorMap = getActiveSensors(sensorLines);

        IList<SensorReading> readingIList = hz.getList("g9_sensors_readings");
        readingIList.clear();

        List<String> lines = prepareCSVLoad(READINGS_FILE_NAME, dir);
        for(String line : lines) {
            String[] values = line.split(";");
            if(sensorMap.containsKey(Long.parseLong(values[7]))) {
                SensorReading sr = new SensorReading(sensorMap.get(Long.parseLong(values[7])).getDescription(), Long.parseLong(values[9]));
                readingIList.add(sr);
            }
        }

        logWithTimeStamp(timestampWriter, "Fin de la lectura del archivo");
    }

    public static void loadQuery2ReadingsFromCSV(String[] args, HazelcastInstance hz, FileWriter timestampWriter) throws IOException {
        String dir = beginCSVLoad(args, timestampWriter);
        List<String> lines = prepareCSVLoad(READINGS_FILE_NAME, dir);

        IList<DayReading> readingIList = hz.getList("g9_sensors_readings");
        readingIList.clear();

        for(String line : lines) {
            String[] values = line.split(";");
                DayReading sr = new DayReading(Long.parseLong(values[2]), values[5], Long.parseLong(values[9]));
                readingIList.add(sr);
        }

        logWithTimeStamp(timestampWriter, "Fin de la lectura del archivo");
    }

    public static void loadQuery3ReadingsFromCSV(String[] args, HazelcastInstance hz, FileWriter timestampWriter, String min) throws IOException {
        String dir = beginCSVLoad(args, timestampWriter);
        List<String> sensorLines = prepareCSVLoad(SENSORS_FILE_NAME, dir);

        Map<Long, ActiveSensor> sensorMap = getActiveSensors(sensorLines);

        List<String> lines = prepareCSVLoad(READINGS_FILE_NAME, dir);

        IList<DateTimeReading> readingIList = hz.getList("g9_sensors_readings");
        readingIList.clear();

        for(String line : lines) {
            String[] values = line.split(";");
            if(sensorMap.containsKey(Long.parseLong(values[7])) && Long.parseLong(values[9]) > Long.parseLong(min)) {
                DateTimeReading sr = new DateTimeReading(Long.parseLong(values[9]),
                        Long.parseLong(values[2]),
                        values[3],
                        Integer.parseInt(values[4]),
                        Integer.parseInt(values[6]),
                        sensorMap.get(Long.parseLong(values[7])).getDescription()
                        );
                readingIList.add(sr);
            }
        }
        logWithTimeStamp(timestampWriter, "Fin de la lectura del archivo");
    }

    public static void loadQuery4ReadingsFromCSV(String[] args, HazelcastInstance hz, FileWriter timestampWriter, Long year) throws IOException {
        String dir = beginCSVLoad(args, timestampWriter);
        List<String> sensorLines = prepareCSVLoad(SENSORS_FILE_NAME, dir);

        Map<Long, ActiveSensor> sensorMap = getActiveSensors(sensorLines);

        List<String> lines = prepareCSVLoad(READINGS_FILE_NAME, dir);

        IList<SensorMonthReading> readingIList = hz.getList("g9_sensors_readings");
        readingIList.clear();

        for(String line : lines) {
            String[] values = line.split(";");
            if(sensorMap.containsKey(Long.parseLong(values[7])) && Long.parseLong(values[2]) == year) {
                SensorMonthReading sr = new SensorMonthReading(Long.parseLong(values[9]),
                        values[3],
                        sensorMap.get(Long.parseLong(values[7])).getDescription()
                );
                readingIList.add(sr);
            }
        }
        logWithTimeStamp(timestampWriter, "Fin de la lectura del archivo");
    }

    private static String beginCSVLoad(String[] args, FileWriter timestampWriter) throws IOException {
        String dir = parseParameter(args, "-DinPath");
        logWithTimeStamp(timestampWriter, "Inicio de la lectura del archivo");
        return dir;
    }

    private static List<String> prepareCSVLoad(String file, String dir) throws IOException {
        List<String> lines = Files.readAllLines(Paths.get(dir + "/" + file), StandardCharsets.ISO_8859_1);
        lines.remove(0);
        return lines;
    }

    private static Map<Long, ActiveSensor> getActiveSensors(List<String> sensorLines) {
        Map<Long, ActiveSensor> sensorMap = new HashMap<>();
        for (String line : sensorLines) {
            String[] values = line.split(";");
            if(Status.valueOf(values[4]).equals(Status.A)) {
                ActiveSensor s = new ActiveSensor(values[1], Long.parseLong(values[0]));
                sensorMap.put(s.getSensorId(), s);
            }
        }
        return sensorMap;
    }
}
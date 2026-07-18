package cl.bci.performance.api.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SystemInfoService {
    public Map<String, Object> resources() {
        Runtime runtime = Runtime.getRuntime();
        Map<String, Object> result = new LinkedHashMap<>();

        result.put("jvm", Map.of(
                "javaVersion", System.getProperty("java.version"),
                "availableProcessors", runtime.availableProcessors(),
                "heapMaxBytes", runtime.maxMemory(),
                "heapTotalBytes", runtime.totalMemory(),
                "heapFreeBytes", runtime.freeMemory(),
                "uptimeMs", ManagementFactory.getRuntimeMXBean().getUptime()
        ));

        Map<String, Object> cgroup = new LinkedHashMap<>();
        cgroup.put("memoryMax", read("/sys/fs/cgroup/memory.max"));
        cgroup.put("memoryCurrent", read("/sys/fs/cgroup/memory.current"));
        cgroup.put("memoryEvents", read("/sys/fs/cgroup/memory.events"));
        cgroup.put("cpuMax", read("/sys/fs/cgroup/cpu.max"));
        cgroup.put("cpuStat", read("/sys/fs/cgroup/cpu.stat"));
        result.put("cgroup", cgroup);

        result.put("pod", Map.of(
                "name", env("POD_NAME"),
                "namespace", env("POD_NAMESPACE"),
                "ip", env("POD_IP"),
                "nodeName", env("NODE_NAME")
        ));

        return result;
    }

    private String read(String path) {
        try {
            Path p = Paths.get(path);
            return Files.exists(p) ? Files.readString(p).trim() : "unavailable";
        } catch (IOException e) {
            return "unavailable: " + e.getMessage();
        }
    }

    private String env(String name) {
        return System.getenv().getOrDefault(name, "unavailable");
    }
}

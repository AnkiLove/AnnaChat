package dev.annachat.config;

import net.momirealms.sparrow.yaml.SparrowYaml;
import net.momirealms.sparrow.yaml.YamlDocument;
import net.momirealms.sparrow.yaml.route.Route;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.ConfigurationSection;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

/** Bukkit configuration facade backed by Sparrow YAML's parser and emitter. */
public class SparrowYamlConfiguration extends MemoryConfiguration {
    private static final SparrowYaml YAML = SparrowYaml.builder().build();

    public static SparrowYamlConfiguration loadConfiguration(File file) {
        SparrowYamlConfiguration configuration = new SparrowYamlConfiguration();
        if (!file.isFile()) return configuration;
        try {
            configuration.load(YAML.load(file));
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取 YAML 文件: " + file, exception);
        }
        return configuration;
    }

    public static SparrowYamlConfiguration loadConfiguration(Reader reader) {
        SparrowYamlConfiguration configuration = new SparrowYamlConfiguration();
        try {
            configuration.load(YAML.load(readerToString(reader)));
        } catch (IOException exception) {
            throw new IllegalStateException("无法读取 YAML 资源", exception);
        }
        return configuration;
    }

    public void save(File file) throws IOException {
        File parent = file.getParentFile();
        if (parent != null) Files.createDirectories(parent.toPath());
        YamlDocument document = YAML.load("");
        for (Map.Entry<String, Object> entry : getValues(true).entrySet()) {
            if (entry.getValue() instanceof ConfigurationSection) continue;
            document.set(Route.from((Object[]) entry.getKey().split("\\.")), normalize(entry.getValue()));
        }
        document.save(file);
    }

    private void load(YamlDocument document) {
        for (Map.Entry<String, Object> entry : document.getValues().entrySet()) {
            loadValue(entry.getKey(), entry.getValue());
        }
    }

    private void loadValue(String path, Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                loadValue(path + "." + entry.getKey(), entry.getValue());
            }
            return;
        }
        set(path, normalize(value));
    }

    private static Object normalize(Object value) {
        if (value instanceof Map<?, ?> map) {
            java.util.LinkedHashMap<String, Object> copy = new java.util.LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                copy.put(String.valueOf(entry.getKey()), normalize(entry.getValue()));
            }
            return copy;
        }
        if (value instanceof List<?> list) return list.stream().map(SparrowYamlConfiguration::normalize).toList();
        return value;
    }

    private static String readerToString(Reader reader) throws IOException {
        StringBuilder result = new StringBuilder();
        char[] buffer = new char[4096];
        int count;
        while ((count = reader.read(buffer)) >= 0) result.append(buffer, 0, count);
        return result.toString();
    }
}

package org.onelyn.gocdcontrib.plugin;

import com.google.gson.GsonBuilder;
import com.thoughtworks.go.plugin.api.GoApplicationAccessor;
import com.thoughtworks.go.plugin.api.GoPlugin;
import com.thoughtworks.go.plugin.api.GoPluginIdentifier;
import com.thoughtworks.go.plugin.api.annotation.Extension;
import com.thoughtworks.go.plugin.api.logging.Logger;
import com.thoughtworks.go.plugin.api.request.GoApiRequest;
import com.thoughtworks.go.plugin.api.request.GoPluginApiRequest;
import com.thoughtworks.go.plugin.api.response.GoApiResponse;
import com.thoughtworks.go.plugin.api.response.GoPluginApiResponse;
import com.thoughtworks.go.plugin.api.task.JobConsoleLogger;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.IOUtils;
import org.onelyn.gocdcontrib.plugin.util.FieldValidator;
import org.onelyn.gocdcontrib.plugin.util.JSONUtils;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

@Extension
public class ScriptRunnerTaskPluginImpl implements GoPlugin {
    private static final Logger LOGGER = Logger.getLoggerFor(ScriptRunnerTaskPluginImpl.class);

    public static final String PLUGIN_ID = "script-runner.task";
    public static final String PLUGIN_DISPLAY_NAME = "Script runner";
    public static final String EXTENSION_NAME = "task";
    private static final List<String> goSupportedVersions = List.of("1.0");

    public static final String PLUGIN_SETTINGS_ENGINE_TYPE = "engine_type";
    public static final String PLUGIN_SETTINGS_SCRIPT = "script";

    public static final String PLUGIN_SETTINGS_GET_CONFIGURATION = "configuration";
    public static final String PLUGIN_SETTINGS_GET_VIEW = "view";
    public static final String PLUGIN_SETTINGS_VALIDATE_CONFIGURATION = "validate";
    public static final String PLUGIN_EXECUTE = "execute";

    public static final String PLUGIN_SETTINGS_DEFAULT_ENGINE_TYPE = "powershell";

    public static final String GET_PLUGIN_SETTINGS = "go.processor.plugin-settings.get";

    public static final int SUCCESS_RESPONSE_CODE = 200;
    public static final int NOT_FOUND_RESPONSE_CODE = 404;
    public static final int INTERNAL_ERROR_RESPONSE_CODE = 500;

    private GoApplicationAccessor goApplicationAccessor;

    @Override
    public void initializeGoApplicationAccessor(GoApplicationAccessor goApplicationAccessor) {
        this.goApplicationAccessor = goApplicationAccessor;
    }

    @Override
    public GoPluginApiResponse handle(GoPluginApiRequest goPluginApiRequest) {
        String requestName = goPluginApiRequest.requestName();
        switch (requestName) {
            case PLUGIN_SETTINGS_GET_CONFIGURATION:
                return handleGetPluginSettingsConfiguration();
            case PLUGIN_SETTINGS_GET_VIEW:
                try {
                    return handleGetPluginSettingsView();
                } catch (IOException e) {
                    return renderJSON(500, String.format("Failed to find template: %s", e.getMessage()));
                }
            case PLUGIN_SETTINGS_VALIDATE_CONFIGURATION:
                return handleValidatePluginSettingsConfiguration(goPluginApiRequest);
            case PLUGIN_EXECUTE:
                return handleExecute(goPluginApiRequest);
        }
        return renderJSON(NOT_FOUND_RESPONSE_CODE, null);
    }

    @Override
    public GoPluginIdentifier pluginIdentifier() {
        return getGoPluginIdentifier();
    }

    private GoPluginApiResponse handleGetPluginSettingsConfiguration() {
        Map<String, Object> response = new HashMap<>();
        response.put(PLUGIN_SETTINGS_ENGINE_TYPE, createField("Engine", PLUGIN_SETTINGS_DEFAULT_ENGINE_TYPE, true, false, "0"));
        response.put(PLUGIN_SETTINGS_SCRIPT, createField("Script", null, true, false, "1"));
        return renderJSON(SUCCESS_RESPONSE_CODE, response);
    }

    private Map<String, Object> createField(String displayName, String defaultValue, boolean isRequired, boolean isSecure, String displayOrder) {
        Map<String, Object> fieldProperties = new HashMap<>();
        fieldProperties.put("display-name", displayName);
        fieldProperties.put("default-value", defaultValue);
        fieldProperties.put("required", isRequired);
        fieldProperties.put("secure", isSecure);
        fieldProperties.put("display-order", displayOrder);
        return fieldProperties;
    }

    private GoPluginApiResponse handleGetPluginSettingsView() throws IOException {
        Map<String, Object> response = new HashMap<>();
        response.put("displayValue", PLUGIN_DISPLAY_NAME);
        response.put("template", IOUtils.toString(getClass().getResourceAsStream("/plugin-settings.template.html"), StandardCharsets.UTF_8));
        return renderJSON(SUCCESS_RESPONSE_CODE, response);
    }

    private void validate(Map<String, Object> response, FieldValidator fieldValidator) {
        fieldValidator.validate(response);
    }

    private GoPluginApiResponse handleValidatePluginSettingsConfiguration(GoPluginApiRequest goPluginApiRequest) {
        Map<String, Object> responseMap = (Map<String, Object>) JSONUtils.fromJSON(goPluginApiRequest.requestBody());
        final Map<String, String> configuration = keyValuePairs(responseMap);
        Map<String, Object> response = new HashMap<>();

        validate(response, fieldValidation -> validateRequiredField(configuration, fieldValidation, PLUGIN_SETTINGS_ENGINE_TYPE, "Engine"));

        validate(response, fieldValidation -> validateRequiredField(configuration, fieldValidation, PLUGIN_SETTINGS_SCRIPT, "Script"));

        return renderJSON(SUCCESS_RESPONSE_CODE, response);
    }

    private void validateRequiredField(Map<String, String> configuration, Map<String, Object> fieldMap, String key, String name) {
        if (configuration.get(key) == null || configuration.get(key).isEmpty()) {
            fieldMap.put("key", key);
            fieldMap.put("message", String.format("'%s' is a required field", name));
        }
    }

    @SuppressWarnings("unchecked")
    private GoPluginApiResponse handleExecute(GoPluginApiRequest goPluginApiRequest) {
        Map<String, Object> response = new HashMap<>();
        String workingDirectory = null;
        String scriptFileName = null;
        boolean isWindows = isWindows();
        try {
            Map<String, Object> responseMap = (Map<String, Object>) JSONUtils.fromJSON(goPluginApiRequest.requestBody());

            Map<String, String> configuration = keyValuePairs(responseMap,"config");
            Map<String, Object> context = (Map<String, Object>) responseMap.get("context");
            workingDirectory = (String) context.get("workingDirectory");
            Map<String, String> environmentVariables = (Map<String, String>) context.get("environmentVariables");

//            Map<String, String> scriptConfig = (Map<String, String>) configuration.get(PLUGIN_SETTINGS_SCRIPT);
            String scriptValue = configuration.get(PLUGIN_SETTINGS_SCRIPT);
            JobConsoleLogger.getConsoleLogger().printLine("\n[script-runner] Script:");
            JobConsoleLogger.getConsoleLogger().printLine("[script-runner] -------------------------");
            JobConsoleLogger.getConsoleLogger().printLine(scriptValue);
            JobConsoleLogger.getConsoleLogger().printLine("[script-runner] -------------------------");
//            Map<String, String> engineTypeConfig = (Map<String, String>) configuration.get(PLUGIN_SETTINGS_ENGINE_TYPE);
            String engineType = configuration.get(PLUGIN_SETTINGS_ENGINE_TYPE);
            if (engineType == null || engineType.trim().equals("")){
                engineType = PLUGIN_SETTINGS_DEFAULT_ENGINE_TYPE;
            }
            JobConsoleLogger.getConsoleLogger().printLine("[script-runner] Engine Type: " + engineType + "\n");

            scriptFileName = generateScriptFileName(engineType, isWindows);

            createScript(workingDirectory, scriptFileName, isWindows, scriptValue);

            int exitCode = executeScript(workingDirectory, engineType, scriptFileName, isWindows, environmentVariables);

            if (exitCode == 0) {
                response.put("success", true);
                response.put("message", "[script-runner] Script completed successfully.");
            } else {
                response.put("success", false);
                response.put("message", "[script-runner] Script completed with exit code: " + exitCode + ".");
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "[script-runner] Script execution interrupted. Reason: " + e.getMessage());
        }
        deleteScript(workingDirectory, scriptFileName);
        return renderJSON(SUCCESS_RESPONSE_CODE, response);
    }

    private boolean isWindows() {
        String osName = System.getProperty("os.name");
        boolean isWindows = osName.toLowerCase().contains("windows");
        JobConsoleLogger.getConsoleLogger().printLine("[script-runner] OS detected: '" + osName + "'. Is Windows? " + isWindows);
        return isWindows;
    }

    private String generateScriptFileName(String engineType, Boolean isWindows) {
        return UUID.randomUUID() + getScriptFilenameExtension(engineType, isWindows);
    }

    private String getScriptFilenameExtension(String engineType, Boolean isWindows) {
        switch (engineType) {
            case "powershell":
            case "pwsh":
                return ".ps1";
            case "onescript":
                return ".os";
        }
        throw new RuntimeException("Unexpected engine type value: " + engineType);
    }

    private Path getScriptPath(String workingDirectory, String scriptFileName) {
        return Paths.get(workingDirectory, scriptFileName);
    }

    private void createScript(String workingDirectory, String scriptFileName, Boolean isWindows, String scriptValue) throws IOException, InterruptedException {
        Path scriptPath = getScriptPath(workingDirectory, scriptFileName);
        FileUtils.writeStringToFile(scriptPath.toFile(), cleanupScript(scriptValue), StandardCharsets.UTF_8);

        if (!isWindows) {
            executeCommand(workingDirectory, null, "chmod", "u+x", scriptFileName);
        }

        JobConsoleLogger.getConsoleLogger().printLine("[script-runner] Script written into '" + scriptPath.toAbsolutePath() + "'.");
    }

    String cleanupScript(String scriptValue) {
        return scriptValue.replaceAll("(\\r\\n|\\n|\\r)", System.getProperty("line.separator"));
    }

    private int executeScript(String workingDirectory, String engineType, String scriptFileName, Boolean isWindows, Map<String, String> environmentVariables) throws IOException, InterruptedException {
        switch (engineType) {
            case "powershell":
                return executeCommand(workingDirectory, environmentVariables, isWindows? "powershell.exe": "pwsh", "-File", scriptFileName);
            case "pwsh":
                return executeCommand(workingDirectory, environmentVariables, "pwsh" + (isWindows? ".exe": ""), "-File", scriptFileName);
            case "onescript":
                if (isWindows) return executeCommand(workingDirectory, environmentVariables, "cmd", "/c", "chcp 65001 & oscript.exe", scriptFileName);
                return executeCommand(workingDirectory, environmentVariables, "oscript", scriptFileName);
        }
        throw new RuntimeException("Unexpected engine type value: " + engineType);
    }

    private int executeCommand(String workingDirectory, Map<String, String> environmentVariables, String... command) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command);
        processBuilder.directory(new File(workingDirectory));
        if (environmentVariables != null && !environmentVariables.isEmpty()) {
            processBuilder.environment().putAll(environmentVariables);
        }
        Process process = processBuilder.start();

        JobConsoleLogger.getConsoleLogger().readOutputOf(process.getInputStream());
        JobConsoleLogger.getConsoleLogger().readErrorOf(process.getErrorStream());

        return process.waitFor();
    }

    private void deleteScript(String workingDirectory, String scriptFileName) {
        if (scriptFileName != null && !scriptFileName.isEmpty()) {
            FileUtils.deleteQuietly(getScriptPath(workingDirectory, scriptFileName).toFile());
        }
    }

    public PluginSettings getPluginSettings() {
        Map<String, Object> requestMap = new HashMap<>();
        requestMap.put("plugin-id", PLUGIN_ID);
        GoApiResponse response = goApplicationAccessor.submit(createGoApiRequest(GET_PLUGIN_SETTINGS, JSONUtils.toJSON(requestMap)));
        if (response.responseBody() == null || response.responseBody().trim().isEmpty()) {
            throw new RuntimeException("plugin is not configured. please provide plugin settings.");
        }
        Map<String, String> responseBodyMap = (Map<String, String>) JSONUtils.fromJSON(response.responseBody());
        return new PluginSettings(responseBodyMap.get(PLUGIN_SETTINGS_ENGINE_TYPE), responseBodyMap.get(PLUGIN_SETTINGS_SCRIPT));
    }

    private boolean isEmpty(String str) {
        return str == null || str.trim().isEmpty();
    }

    private Map<String, String> keyValuePairs(Map<String, Object> map, String mainKey) {
        return keyValuePairs((Map<String, Object>) map.get(mainKey));
    }

    private Map<String, String> keyValuePairs(Map<String, Object> fieldsMap) {
        Map<String, String> keyValuePairs = new HashMap<>();
        for (String field : fieldsMap.keySet()) {
            Map<String, Object> fieldProperties = (Map<String, Object>) fieldsMap.get(field);
            String value = (String) fieldProperties.get("value");
            keyValuePairs.put(field, value);
        }
        return keyValuePairs;
    }

    private GoPluginIdentifier getGoPluginIdentifier() {
        return new GoPluginIdentifier(EXTENSION_NAME, goSupportedVersions);
    }

    private GoApiRequest createGoApiRequest(final String api, final String responseBody) {
        return new GoApiRequest() {
            @Override
            public String api() {
                return api;
            }

            @Override
            public String apiVersion() {
                return "1.0";
            }

            @Override
            public GoPluginIdentifier pluginIdentifier() {
                return getGoPluginIdentifier();
            }

            @Override
            public Map<String, String> requestParameters() {
                return null;
            }

            @Override
            public Map<String, String> requestHeaders() {
                return null;
            }

            @Override
            public String requestBody() {
                return responseBody;
            }
        };
    }

    private GoPluginApiResponse renderJSON(final int responseCode, Object response) {
        final String json = response == null ? null : new GsonBuilder().create().toJson(response);
        return new GoPluginApiResponse() {
            @Override
            public int responseCode() {
                return responseCode;
            }

            @Override
            public Map<String, String> responseHeaders() {
                return null;
            }

            @Override
            public String responseBody() {
                return json;
            }
        };
    }
}

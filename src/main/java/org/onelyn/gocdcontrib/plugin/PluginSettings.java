package org.onelyn.gocdcontrib.plugin;

public class PluginSettings {
    private String engineType;
    private String script;

    public PluginSettings(String engineType, String script) {
        this.engineType = engineType;
        this.script = script;
    }

    public String getEngineType() {
        return engineType;
    }

    public void setEngineType(String engineType) {
        this.engineType = engineType;
    }

    public String getScript() {
        return script;
    }

    public void setScript(String script) {
        this.script = script;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        PluginSettings that = (PluginSettings) o;

        if (!engineType.equals(that.engineType)) return false;
        if (!script.equals(that.script)) return false;

        return true;
    }

    @Override
    public int hashCode() {
        int result = engineType != null ? engineType.hashCode() : 0;
        result = 31 * result + (script != null ? script.hashCode() : 0);

        return result;
    }
}

package moe.plushie.armourers_workshop.loom.core;

import groovy.json.JsonBuilder;
import groovy.json.JsonSlurperClassic;

import java.io.StringReader;
import java.util.Map;

public class JsonTransformer {

    private final String contents;
    private final Object object;

    public JsonTransformer(String contents) {
        this.contents = contents;
        this.object = new JsonSlurperClassic().parse(new StringReader(contents));
    }

    public void rename(String keyIn, String keyOut) {
        var ins = keyIn.split("\\.");
        var outs = keyOut.split("\\.");
        if (ins.length == 0 || outs.length == 0) {
            return;

        }
        // find the value
        var value = object;
        for (int i = 1; i <= ins.length; ++i) {
            if (value instanceof Map<?, ?> map) {
                if (i < ins.length) {
                    value = map.get(ins[i - 1]);
                } else {
                    value = map.remove(ins[i - 1]);
                }
            } else {
                value = null;
            }
        }
        // set the value
        var target = object;
        for (int i = 1; i <= outs.length; ++i) {
            if (target instanceof Map<?, ?> map) {
                if (i < outs.length) {
                    target = map.get(outs[i - 1]);
                } else {
                    ((Map<String, Object>) map).put(outs[i - 1], value);
                    break;
                }
            } else {
                target = null;
            }
        }
    }

    public String build() {
        return new JsonBuilder(object).toString();
    }
}

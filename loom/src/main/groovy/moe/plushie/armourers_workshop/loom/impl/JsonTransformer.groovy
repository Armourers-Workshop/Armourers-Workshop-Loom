package moe.plushie.armourers_workshop.loom.impl

import groovy.json.JsonBuilder
import groovy.json.JsonSlurperClassic

class JsonTransformer {

    private String contents
    private Object object

    JsonTransformer(String contents) {
        this.contents = contents
        this.object = new JsonSlurperClassic().parse(new StringReader(contents));
    }

    void rename(String keyIn, String keyOut) {
        def ins = keyIn.split("\\.")
        def outs = keyOut.split("\\.")
        if (ins.length == 0 || outs.length == 0) {
            return
        }
        // find the value
        def value = object
        for (int i = 1; i <= ins.length; ++i) {
            if (value instanceof Map) {
                if (i < ins.length) {
                    value = value.get(ins[i - 1])
                } else {
                    value = value.remove(ins[i - 1])
                }
            } else {
                value = null
            }
        }
        // set the value
        def target = object
        for (int i = 1; i <= outs.length; ++i) {
            if (target instanceof Map) {
                if (i < outs.length) {
                    target = target.get(outs[i - 1])
                } else {
                    target.put(outs[i - 1], value)
                    break
                }
            } else {
                target = null
            }
        }
    }

    String build() {
        return new JsonBuilder(object).toString()
    }
}

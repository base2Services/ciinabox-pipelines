// Copied from https://github.com/jenkinsci/aws-sam-plugin IntrinsicsYamlConstructor.java

package com.base2.ciinabox.aws

import java.util.Arrays
import java.util.HashMap
import java.util.Map

import org.yaml.snakeyaml.constructor.AbstractConstruct
import org.yaml.snakeyaml.constructor.SafeConstructor
import org.yaml.snakeyaml.error.YAMLException
import org.yaml.snakeyaml.nodes.MappingNode
import org.yaml.snakeyaml.nodes.Node
import org.yaml.snakeyaml.nodes.ScalarNode
import org.yaml.snakeyaml.nodes.SequenceNode
import org.yaml.snakeyaml.nodes.Tag
import org.yaml.snakeyaml.LoaderOptions

import com.cloudbees.groovy.cps.NonCPS

/**
 * Allows snakeyaml to parse YAML templates that contain short forms of
 * CloudFormation intrinsic functions.
 * 
 */
public class IntrinsicsYamlConstructor extends SafeConstructor implements Serializable {
    public IntrinsicsYamlConstructor() {
        super(new LoaderOptions())
        this.yamlConstructors.put(new Tag("!And"), new ConstructFunction(true, false))
        this.yamlConstructors.put(new Tag("!Base64"), new ConstructFunction(true, false))
        this.yamlConstructors.put(new Tag("!Cidr"), new ConstructFunction(true, false))
        this.yamlConstructors.put(new Tag("!Condition"), new ConstructFunction(true, false))
        this.yamlConstructors.put(new Tag("!Equals"), new ConstructFunction(true, false))
        this.yamlConstructors.put(new Tag("!FindInMap"), new ConstructFunction(true, false))
        this.yamlConstructors.put(new Tag("!GetAtt"), new ConstructFunction(true, true))
        this.yamlConstructors.put(new Tag("!GetAZs"), new ConstructFunction(true, false))
        this.yamlConstructors.put(new Tag("!If"), new ConstructFunction(true, false))
        this.yamlConstructors.put(new Tag("!ImportValue"), new ConstructFunction(true, false))
        this.yamlConstructors.put(new Tag("!Join"), new ConstructFunction(true, false))
        this.yamlConstructors.put(new Tag("!Not"), new ConstructFunction(true, false))
        this.yamlConstructors.put(new Tag("!Or"), new ConstructFunction(true, false))
        this.yamlConstructors.put(new Tag("!Ref"), new ConstructFunction(false, false))
        this.yamlConstructors.put(new Tag("!Select"), new ConstructFunction(true, false))
        this.yamlConstructors.put(new Tag("!Split"), new ConstructFunction(true, false))
        this.yamlConstructors.put(new Tag("!Sub"), new ConstructFunction(true, false))
    }

    private class ConstructFunction extends AbstractConstruct {
        private final boolean attachFnPrefix
        private final boolean forceSequenceValue

        public ConstructFunction(boolean attachFnPrefix, boolean forceSequenceValue) {
            this.attachFnPrefix = attachFnPrefix
            this.forceSequenceValue = forceSequenceValue
        }

        @NonCPS
        public Object construct(Node node) {
            String key = node.getTag().getValue().substring(1)
            String prefix = attachFnPrefix ? "Fn::" : ""
            Map<String, Object> result = new HashMap<String, Object>()

            result.put(prefix + key, constructIntrinsicValueObject(node))
            return result
        }
        
        @NonCPS
        protected Object constructIntrinsicValueObject(Node node) {
            if (node instanceof ScalarNode) {
                Object val = (String) constructScalar((ScalarNode) node)
                if (forceSequenceValue) {
                    val = Arrays.asList(((String) val).split("\\."))
                }
                return val
            } else if (node instanceof SequenceNode) {
                return constructSequence((SequenceNode) node)
            } else if (node instanceof MappingNode) {
                return constructMapping((MappingNode) node)
            }
            throw new YAMLException("Intrisic function arguments cannot be parsed.")
        }
    }
}
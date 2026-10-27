package dev.jdoc;

import java.util.ArrayList;
import java.util.List;

final class DocModel {

  int schemaVersion;
  String name;
  String sourceUrl;
  String release;
  String description;
  List<Block> classBody = new ArrayList<>();
  String since;
  boolean deprecated;
  String deprecatedSince;
  boolean preview;
  transient boolean stale;
  List<Method> methods = new ArrayList<>();
  List<Method> constructors = new ArrayList<>();
  List<Method> fields = new ArrayList<>();
  List<Method> enumConstants = new ArrayList<>();
  List<Method> nestedTypes = new ArrayList<>();
  List<Method> inherited = new ArrayList<>();

  List<Method> declaredMembers() {
    List<Method> result = new ArrayList<>(methods);
    result.addAll(constructors);
    result.addAll(fields);
    result.addAll(enumConstants);
    result.addAll(nestedTypes);
    return result;
  }

  static final class Method {

    String name;
    String signature;
    String returnType;
    String description;
    String details;
    List<Block> body = new ArrayList<>();
    String since;
    boolean deprecated;
    String deprecatedSince;
    boolean preview;
    String anchor;
    List<String> parameterTypes = new ArrayList<>();
    String kind = "method";
    String declaringType;
    String sourceUrl;
    boolean inherited;
  }

  static final class Block {
    String kind;
    List<Inline> inlines = new ArrayList<>();
    String code;
    List<Inline> codeLinks = new ArrayList<>();
    List<List<Block>> items = new ArrayList<>();
  }

  static final class Inline {
    String text;
    String targetClass;
    String targetAnchor;
    String externalUrl;
  }
}

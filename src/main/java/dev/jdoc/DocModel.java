package dev.jdoc;

import java.util.ArrayList;
import java.util.List;

final class DocModel {

  int schemaVersion;
  String name;
  String sourceUrl;
  String release;
  String description;
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
    String description;
    String details;
    String anchor;
    List<String> parameterTypes = new ArrayList<>();
    String kind = "method";
    String declaringType;
    String sourceUrl;
    boolean inherited;
  }
}

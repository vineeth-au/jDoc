package dev.jdoc;

import java.util.ArrayList;
import java.util.List;

final class DocModel {

  int schemaVersion;
  String name;
  String sourceUrl;
  String release;
  String description;
  List<Method> methods = new ArrayList<>();
  List<Method> constructors = new ArrayList<>();

  static final class Method {

    String name;
    String signature;
    String description;
    String details;
    String anchor;
    List<String> parameterTypes = new ArrayList<>();
  }
}

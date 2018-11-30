package org.sonarsource.sjava.converter;

import java.util.Objects;
import org.sonarsource.slang.api.NativeKind;

public class SJavaNativeKind implements NativeKind {

  private final String type;

  public SJavaNativeKind(String type) {
    this.type = type;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    SJavaNativeKind that = (SJavaNativeKind) o;
    return Objects.equals(type, that.type);
  }

  @Override
  public int hashCode() {
    return Objects.hash(type);
  }

  @Override
  public String toString() {
    return type;
  }
}

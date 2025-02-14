/*
 * SkyClaims - A Skyblock plugin made for Sponge
 * Copyright (C) 2017 Mohron
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * SkyClaims is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with SkyClaims.  If not, see <http://www.gnu.org/licenses/>.
 */

package net.mohron.skyclaims.integration;

import org.apache.commons.lang3.text.StrBuilder;

import javax.annotation.Nonnull;

public class Version implements Comparable<Version> {

  @Nonnull
  private final int[] numbers;

  private Version(@Nonnull String version) {
    final String split[] = version.split("-")[0].split(".");
    numbers = new int[split.length];
    for (int i = 0; i < split.length; i++) {
      numbers[i] = Integer.valueOf(split[i]);
    }
  }

  public static Version of(@Nonnull String string) {
    return new Version(string);
  }

  @Override
  public int compareTo(@Nonnull Version other) {
    final int maxLength = Math.max(numbers.length, other.numbers.length);
    for (int i = 0; i < maxLength; i++) {
      final int left = i < numbers.length ? numbers[i] : 0;
      final int right = i < other.numbers.length ? other.numbers[i] : 0;
      if (left != right) {
        return left < right ? -1 : 1;
      }
    }
    return 0;
  }

  public int compareTo(@Nonnull String other) {
    return compareTo(new Version(other));
  }

  @Override
  public String toString() {
    if (numbers.length == 0) {
      return "";
    }
    StrBuilder version = new StrBuilder();
    version.append(numbers[0]);
    for (int i = 1; i < numbers.length; i++) {
      version.append(".");
      version.append(numbers[i]);
    }
    return version.build();
  }
}

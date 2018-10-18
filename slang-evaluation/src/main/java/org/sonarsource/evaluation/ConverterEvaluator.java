/*
 * SonarSource SLang
 * Copyright (C) 2009-2018 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarsource.evaluation;

import org.sonarsource.slang.api.ASTConverter;
import org.sonarsource.slang.api.NativeKind;
import org.sonarsource.slang.api.ParseException;
import org.sonarsource.slang.api.Tree;
import org.sonarsource.slang.impl.NativeTreeImpl;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import static java.util.Map.Entry.comparingByValue;
import static java.util.stream.Collectors.toMap;

class ConverterEvaluator {
  private ASTConverter converter;
  private String sourceTestFilesFolder;
  private String sourceExtension;

  private double totalPercentage = 0;
  private int nFiles = 0;

  private Map<String,Integer> natNodeType;

  ConverterEvaluator(ASTConverter converter, String sourceTestFilesFolder, String sourceExtension) {
    this.converter = converter;
    this.sourceTestFilesFolder = sourceTestFilesFolder;
    this.sourceExtension = sourceExtension;
    natNodeType = new HashMap<>();
  }

  void evaluate() throws IOException {
    String fileName = "slang-evaluation\\src\\main\\output\\" + sourceExtension + "_evaluation.txt";
    try(BufferedWriter writer = new BufferedWriter(new FileWriter(fileName))) {
      try (Stream<Path> str = Files.walk(Paths.get(sourceTestFilesFolder))) {
        str.filter(p -> p.toString().endsWith(sourceExtension))
            .forEach(p -> {
              try {
                convertCode(p, writer);
              } catch (IOException e) {
                e.printStackTrace();
              }
            });
      }
      if(nFiles != 0) {
        writer.write("Total native percentage: " + (totalPercentage / nFiles)*100 + "%, on "+ nFiles +" files\n");
      }
    }

    try(BufferedWriter writer = new BufferedWriter(new FileWriter(fileName + "_native_nodes.csv"))) {
      int totalNative = natNodeType.values().stream().mapToInt(Integer::intValue).sum();

      Map<String,Integer> sorted = natNodeType
          .entrySet()
          .stream()
          .sorted(Collections.reverseOrder(comparingByValue()))
          .collect(
              toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e2,
                  LinkedHashMap::new));
      writer.write("Native node type, Number of occurrences, Percentage of native \n");
      if(totalNative != 0){
        for (Map.Entry<String, Integer> e : sorted.entrySet()) {
          writer.write(e.getKey() + "," + e.getValue() + ", " + (e.getValue() / (double)totalNative)*100 + "%" + "\n");
        }
      }
    }


  }

  private void convertCode(Path p, BufferedWriter writer) throws IOException {
    byte[] encoded = Files.readAllBytes(p);
    String code = new String(encoded, Charset.defaultCharset());
    try {
      double percentage = getNativeNodePercentage(code);
      nFiles ++;
      totalPercentage += percentage;
      //writer.write(p.toString() + ": " + percentage + "%\n");
   } catch (ParseException e) {
      System.out.println("Parse exception in file: " + p.toString());
    }

  }

  private Double getNativeNodePercentage(String code) {
    Tree convertedTree = converter.parse(code);

    long totalNodes = convertedTree.descendants().count();

    convertedTree.descendants()
        .filter(t -> t instanceof NativeTreeImpl).forEach(n ->
          natNodeType.merge(((NativeTreeImpl) n).nativeKind().toString(), 1, Integer::sum)
        );
    long nativeNodes = convertedTree.descendants()
        .filter(t -> t instanceof NativeTreeImpl).count();

    if(totalNodes == 0){
      return 0.0;
    } else {
      return nativeNodes/(double)totalNodes;
    }
  }
}

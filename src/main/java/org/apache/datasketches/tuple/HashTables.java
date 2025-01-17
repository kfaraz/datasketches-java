/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.datasketches.tuple;

import static java.lang.Math.ceil;
import static java.lang.Math.max;
import static java.lang.Math.min;
import static org.apache.datasketches.HashOperations.hashInsertOnly;
import static org.apache.datasketches.HashOperations.hashSearch;
import static org.apache.datasketches.Util.MIN_LG_NOM_LONGS;
import static org.apache.datasketches.Util.ceilingPowerOf2;

import java.lang.reflect.Array;

@SuppressWarnings("unchecked")
class HashTables<S extends Summary> {
  long[] hashTable_ = null;
  S[] summaryTable_ = null;
  int lgTableSize_ = 0;
  int count_ = 0;

  HashTables() { }

  //must have valid entries
  void fromSketch(final Sketch<S> sketch) {
    count_ = sketch.getRetainedEntries();
    lgTableSize_ = getLgTableSize(count_);

    hashTable_ = new long[1 << lgTableSize_];
    final SketchIterator<S> it = sketch.iterator();
    while (it.next()) {
      final long hash = it.getHash();
      final int index = hashInsertOnly(hashTable_, lgTableSize_, hash);
      final S mySummary = (S)it.getSummary().copy();
      if (summaryTable_ == null) {
        summaryTable_ = (S[]) Array.newInstance(mySummary.getClass(), 1 << lgTableSize_);
      }
      summaryTable_[index] = mySummary;
    }
  }

  //must have valid entries
  void fromSketch(final org.apache.datasketches.theta.Sketch sketch, final S summary) {
    count_ = sketch.getRetainedEntries(true);
    lgTableSize_ = getLgTableSize(count_);

    hashTable_ = new long[1 << lgTableSize_];
    final org.apache.datasketches.theta.HashIterator it = sketch.iterator();
    while (it.next()) {
      final long hash = it.get();
      final int index = hashInsertOnly(hashTable_, lgTableSize_, hash);
      final S mySummary = (S)summary.copy();
      if (summaryTable_ == null) {
        summaryTable_ = (S[]) Array.newInstance(mySummary.getClass(), 1 << lgTableSize_);
      }
      summaryTable_[index] = mySummary;
    }
  }

  private void fromArrays(final long[] hashArr, final S[] summaryArr, final int count) {
    count_ = count;
    lgTableSize_ = getLgTableSize(count);

    summaryTable_ = null;
    hashTable_ = new long[1 << lgTableSize_];
    for (int i = 0; i < count; i++) {
      final long hash = hashArr[i];
      final int index = hashInsertOnly(hashTable_, lgTableSize_, hash);
      final S mySummary = summaryArr[i];
      if (summaryTable_ == null) {
        summaryTable_ = (S[]) Array.newInstance(mySummary.getClass(), 1 << lgTableSize_);
      }
      summaryTable_[index] = summaryArr[i];
    }
  }

  //For Tuple Sketches
  HashTables<S> getIntersectHashTables(
      final Sketch<S> nextTupleSketch,
      final long thetaLong,
      final SummarySetOperations<S> summarySetOps) {

    final Class<S> summaryType = (Class<S>) summaryTable_.getClass().getComponentType();

    //Match nextSketch data with local instance data, filtering by theta
    final int maxMatchSize = min(count_, nextTupleSketch.getRetainedEntries());
    final long[] matchHashArr = new long[maxMatchSize];
    final S[] matchSummariesArr = (S[]) Array.newInstance(summaryType, maxMatchSize);
    int matchCount = 0;
    final SketchIterator<S> it = nextTupleSketch.iterator();

    while (it.next()) {
      final long hash = it.getHash();
      if (hash >= thetaLong) { continue; }
      final int index = hashSearch(hashTable_, lgTableSize_, hash);
      if (index < 0) { continue; }
      //Copy the intersecting items from local hashTables_
      // sequentially into local matchHashArr_ and matchSummaries_
      matchHashArr[matchCount] = hash;
      matchSummariesArr[matchCount] = summarySetOps.intersection(summaryTable_[index], it.getSummary());
      matchCount++;
    }
    final HashTables<S> resultHT = new HashTables<>();
    resultHT.fromArrays(matchHashArr, matchSummariesArr, matchCount);
    return resultHT;
  }

  //For Theta Sketches
  HashTables<S> getIntersectHashTables(
      final org.apache.datasketches.theta.Sketch nextThetaSketch,
      final long thetaLong,
      final SummarySetOperations<S> summarySetOps,
      final S summary) {

    final Class<S> summaryType = (Class<S>) summary.getClass();

    //Match nextSketch data with local instance data, filtering by theta
    final int maxMatchSize = min(count_, nextThetaSketch.getRetainedEntries());
    final long[] matchHashArr = new long[maxMatchSize];
    final S[] matchSummariesArr = (S[]) Array.newInstance(summaryType, maxMatchSize);
    int matchCount = 0;
    final org.apache.datasketches.theta.HashIterator it = nextThetaSketch.iterator();

    //scan B & search A(hashTable) for match
    while (it.next()) {
      final long hash = it.get();
      if (hash >= thetaLong) { continue; }
      final int index = hashSearch(hashTable_, lgTableSize_, hash);
      if (index < 0) { continue; }
      //Copy the intersecting items from local hashTables_
      // sequentially into local matchHashArr_ and matchSummaries_
      matchHashArr[matchCount] = hash;
      matchSummariesArr[matchCount] = summarySetOps.intersection(summaryTable_[index], summary);
      matchCount++;
    }
    final HashTables<S> resultHT = new HashTables<>();
    resultHT.fromArrays(matchHashArr, matchSummariesArr, matchCount);
    return resultHT;
  }

  void clear() {
    hashTable_ = null;
    summaryTable_ = null;
    lgTableSize_ = 0;
    count_ = 0;
  }

  static int getLgTableSize(final int count) {
    final int tableSize = max(ceilingPowerOf2((int) ceil(count / 0.75)), 1 << MIN_LG_NOM_LONGS);
    return Integer.numberOfTrailingZeros(tableSize);
  }

}

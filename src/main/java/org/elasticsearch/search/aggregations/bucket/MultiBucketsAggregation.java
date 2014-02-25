/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.search.aggregations.bucket;

import org.elasticsearch.ElasticsearchIllegalArgumentException;
import org.elasticsearch.common.text.Text;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.Aggregations;
import org.elasticsearch.search.aggregations.metrics.MetricsAggregation;

import java.util.Collection;

/**
 * An aggregation that returns multiple buckets
 */
public interface MultiBucketsAggregation extends Aggregation {


    /**
     * A bucket represents a criteria to which all documents that fall in it adhere to. It is also uniquely identified
     * by a key, and can potentially hold sub-aggregations computed over all documents in it.
     */
    public interface Bucket {

        /**
         * @return  The key associated with the bucket as a string
         */
        String getKey();

        /**
         * @return  The key associated with the bucket as text (ideal for further streaming this instance)
         */
        Text getKeyAsText();

        /**
         * @return The number of documents that fall within this bucket
         */
        long getDocCount();

        /**
         * @return  The sub-aggregations of this bucket
         */
        Aggregations getAggregations();

        static class SubAggregationComparator<B extends Bucket> implements java.util.Comparator<B> {

            private final String aggName;
            private final String[] aggNameParts;
            private final String valueName;
            private final boolean asc;

            public SubAggregationComparator(String expression, boolean asc) {
                this.asc = asc;
                int i = expression.lastIndexOf('.');
                if (i < 0) {
                    this.aggName = expression;
                    this.valueName = null;
                } else {
                    this.aggName = expression.substring(0, i);
                    this.valueName = expression.substring(i+1);
                }
                this.aggNameParts = this.aggName.split("\\.");
            }

            public SubAggregationComparator(String aggName, String valueName, boolean asc) {
                this.aggName = aggName;
                this.aggNameParts = this.aggName.split("\\.");
                this.valueName = valueName;
                this.asc = asc;
            }

            public boolean asc() {
                return asc;
            }

            public String aggName() {
                return aggName;
            }

            public String valueName() {
                return valueName;
            }

            @Override
            public int compare(B b1, B b2) {
                double v1 = value(b1);
                double v2 = value(b2);
                return asc ? Double.compare(v1, v2) : Double.compare(v2, v1);
            }

            private double value(B bucket) {
                System.out.println(aggName + ": " + bucket.getKey());
                Aggregation aggregation = bucket.getAggregations().get(aggNameParts[0]);
                
                if (aggNameParts.length > 1) {
                    for (int i = 1; aggregation != null && i < aggNameParts.length; i++) {
                        if (!(aggregation instanceof SingleBucketAggregation)) {
                            throw new ElasticsearchIllegalArgumentException("Cannot traverse sub-aggregation [" + aggName + "] on depth " + i);
                        }
                        SingleBucketAggregation sba = (SingleBucketAggregation) aggregation;
                        aggregation = sba.getAggregations().get(aggNameParts[i]);
                    }
                }
                
                if (aggregation == null) {
                    throw new ElasticsearchIllegalArgumentException("Unknown aggregation named [" + aggName + "]");
                }
                if (aggregation instanceof SingleBucketAggregation) {
                    if ("_count".equals(valueName)) {
                        return ((SingleBucketAggregation)aggregation).getDocCount();
                    }
                    aggregation = ((SingleBucketAggregation)aggregation).getAggregations().get(valueName);
                    System.out.println("taking sub-agg " + valueName + " aggregation: " + aggregation);
                    if (aggregation == null) {
                        throw new ElasticsearchIllegalArgumentException("Cannot sort aggregation on [" + valueName + "].");
                    }
                }
                if (aggregation instanceof MetricsAggregation.SingleValue) {
                    //TODO should we throw an exception if the value name is specified?
                    System.out.println("Singlevalue");
                    return ((MetricsAggregation.SingleValue) aggregation).value();
                }
                if (aggregation instanceof MetricsAggregation.MultiValue) {
                    if (valueName == null) {
                        throw new ElasticsearchIllegalArgumentException("Cannot sort on multi valued aggregation [" + aggName + "]. A value name is required");
                    }
                    return ((MetricsAggregation.MultiValue) aggregation).value(valueName);
                }

                throw new ElasticsearchIllegalArgumentException("A mal attempt to sort terms by aggregation [" + aggregation.getName() +
                        "]. Terms can only be ordered by either standard order or direct calc aggregators of the terms");
            }
        }
    }

    /**
     * @return  The buckets of this aggregation.
     */
    Collection<? extends Bucket> getBuckets();

    /**
     * The bucket that is associated with the given key.
     *
     * @param key   The key of the requested bucket.
     * @return      The bucket
     */
    <B extends Bucket> B getBucketByKey(String key);
}

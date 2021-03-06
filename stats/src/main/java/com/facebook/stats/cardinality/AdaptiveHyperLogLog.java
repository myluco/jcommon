package com.facebook.stats.cardinality;

import com.google.common.base.Preconditions;

import javax.annotation.concurrent.NotThreadSafe;

import static com.facebook.stats.cardinality.BucketAndHash.fromHash;
import static com.facebook.stats.cardinality.HyperLogLogUtil.computeHash;
import static com.google.common.base.Preconditions.checkArgument;

@NotThreadSafe
public class AdaptiveHyperLogLog {
  private static final int INSTANCE_SIZE = UnsafeUtil.sizeOf(AdaptiveHyperLogLog.class);

  private Estimator estimator;

  public AdaptiveHyperLogLog(int numberOfBuckets) {
    Preconditions.checkArgument(
      Numbers.isPowerOf2(numberOfBuckets),
      "numberOfBuckets must be a power of 2"
    );

    this.estimator = new SparseEstimator(numberOfBuckets);
  }

  public AdaptiveHyperLogLog(int[] buckets)
  {
    checkArgument(Numbers.isPowerOf2(buckets.length), "numberOfBuckets must be a power of 2");

    estimator = makeEstimator(buckets);
  }

  /**
   * @return true if the estimation was affected by this addition
   */
  public boolean add(long value) {
    BucketAndHash bucketAndHash = fromHash(computeHash(value), estimator.getNumberOfBuckets());
    int lowestBitPosition = Long.numberOfTrailingZeros(bucketAndHash.getHash()) + 1;

    if (estimator.getClass() == SparseEstimator.class &&
      (estimator.estimateSizeInBytes() >= DenseEstimator.estimateSizeInBytes(estimator.getNumberOfBuckets())
         || lowestBitPosition >= SparseEstimator.MAX_BUCKET_VALUE)) {
      estimator = new DenseEstimator(estimator.buckets());
    }

    return estimator.setIfGreater(bucketAndHash.getBucket(), lowestBitPosition);
  }

  public long estimate() {
    return estimator.estimate();
  }

  public int getSizeInBytes() {
    return estimator.estimateSizeInBytes() + INSTANCE_SIZE;
  }

  public int getNumberOfBuckets() {
    return estimator.getNumberOfBuckets();
  }

  public int[] buckets() {
    return estimator.buckets();
  }

  public void merge(AdaptiveHyperLogLog other) {
    estimator = makeEstimator(HyperLogLogUtil.mergeBuckets(this.buckets(), other.buckets()));
  }

  public static AdaptiveHyperLogLog merge(AdaptiveHyperLogLog first, AdaptiveHyperLogLog second) {
    return new AdaptiveHyperLogLog(HyperLogLogUtil.mergeBuckets(first.buckets(), second.buckets()));
  }

  private static Estimator makeEstimator(int[] buckets) {
    int nonZeroBuckets = 0;
    int maxValue = 0;
    for (int value : buckets) {
      maxValue = Math.max(maxValue, value);
      if (value > 0) {
        ++nonZeroBuckets;
      }
    }

    if (maxValue < SparseEstimator.MAX_BUCKET_VALUE &&
      SparseEstimator.estimateSizeInBytes(nonZeroBuckets, buckets.length) < DenseEstimator.estimateSizeInBytes(buckets.length)) {
      return new SparseEstimator(buckets);
    }
    else {
      return new DenseEstimator(buckets);
    }
  }

}

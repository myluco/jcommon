package com.facebook.stats;

import org.joda.time.ReadableDateTime;
import org.joda.time.ReadableDuration;

import java.util.Arrays;
import java.util.Iterator;

public class CompositeMax extends AbstractCompositeCounter<EventCounter>
  implements EventCounter {

  public CompositeMax(
    ReadableDuration maxLength, ReadableDuration maxChunkLength
  ) {
    super(maxLength, maxChunkLength);
  }

  public CompositeMax(ReadableDuration maxLength) {
    super(maxLength);
  }

  @Override
  public EventCounter merge(EventCounter counter) {
    if (counter instanceof CompositeMax) {
      return internalMerge(
        ((CompositeMax) counter).getEventCounters(),
        new CompositeMax(getMaxLength(), getMaxChunkLength())
      );
    } else {
      return internalMerge(
        Arrays.asList(counter),
        new CompositeMax(getMaxLength(), getMaxChunkLength())
      );
    }
  }

  @Override
  protected EventCounter nextCounter(
    ReadableDateTime start, ReadableDateTime end
  ) {
    return new MaxEventCounter(start, end);
  }

  @Override
  public synchronized long getValue() {
    trimIfNeeded();

    long max = Long.MIN_VALUE;

    for (EventCounter eventCounter : getEventCounters()) {
      max = Math.max(max, eventCounter.getValue());
    }

    return max;
  }
}

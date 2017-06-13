package com.netflix.simianarmy.aws.janitor.rule.volume;

import java.util.Date;

import org.apache.commons.lang.Validate;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.netflix.simianarmy.MonkeyCalendar;
import com.netflix.simianarmy.Resource;
import com.netflix.simianarmy.aws.AWSResource;
import com.netflix.simianarmy.janitor.JanitorMonkey;
import com.netflix.simianarmy.janitor.Rule;

public abstract class AbstractVolumeRule implements Rule {

    protected static final Logger LOGGER = LoggerFactory.getLogger(AbstractVolumeRule.class);

    /**
     * The date format used to print or parse the user specified termination date.
     **/
    protected static final DateTimeFormatter TERMINATION_DATE_FORMATTER = DateTimeFormat.forPattern("yyyy-MM-dd");
    protected final MonkeyCalendar calendar;
    protected final int retentionDays;

    public AbstractVolumeRule(
        MonkeyCalendar calendar, int retentionDays) {
        Validate.notNull(calendar);
        Validate.isTrue(retentionDays >= 0);
        this.calendar = calendar;
        this.retentionDays = retentionDays;
    }

    public boolean isValid(Resource resource) {
        Validate.notNull(resource);
        if (!resource.getResourceType().name().equals("EBS_VOLUME")) {
            return true;
        }

        // The state of the volume being "available" means that it is not attached to any instance.
        if (!"available".equals(((AWSResource) resource).getAWSResourceState())) {
            return true;
        }
        String janitorTag = resource.getTag(JanitorMonkey.JANITOR_TAG);
        if (janitorTag != null) {
            if ("donotmark".equals(janitorTag)) {
                LOGGER.info(String.format("The volume %s is tagged as not handled by Janitor",
                    resource.getId()));
                return true;
            }
            try {
                // Owners can tag the volume with a termination date in the "janitor" tag.
                Date userSpecifiedDate = new Date(
                    TERMINATION_DATE_FORMATTER.parseDateTime(janitorTag).getMillis());
                resource.setExpectedTerminationTime(userSpecifiedDate);
                resource.setTerminationReason(String.format("User specified termination date %s", janitorTag));
                return false;
            } catch (Exception e) {
                LOGGER.error(String.format("The janitor tag is not a user specified date: %s", janitorTag));
            }
        }

        return validateInternal(resource);

    }

    protected abstract boolean validateInternal(Resource resource);
}

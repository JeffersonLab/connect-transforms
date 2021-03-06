package org.jlab.jaws;

import io.confluent.connect.avro.AvroData;
import io.confluent.connect.avro.AvroDataConfig;
import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.data.SchemaBuilder;
import org.apache.kafka.connect.data.Struct;
import org.apache.kafka.connect.source.SourceRecord;
import org.jlab.jaws.entity.ActiveAlarm;
import org.junit.After;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class EpicsToAlarmValueTest {
    private EpicsToAlarm<SourceRecord> xform = new EpicsToAlarm.Value<>();

    public final Schema INPUT_VALUE_SCHEMA = SchemaBuilder.struct()
            .name("org.jlab.kafka.connect.EPICS_CA_DBR").version(1).doc("An EPICS Channel Access (CA) Time Database Record (DBR) MonitorEvent value")
            .field("timestamp", SchemaBuilder.int64().doc("UNIX timestamp (seconds from epoch - Jan. 1 1970 UTC less leap seconds)").build())
            .field("status", SchemaBuilder.int8().optional().doc("CA Alarm Status: 0=NO_ALARM,1=READ,2=WRITE,3=HIHI,4=HIGH,5=LOLO,6=LOW,7=STATE,8=COS,9=COMM,10=TIMEOUT,11=HW_LIMIT,12=CALC,13=SCAN,14=LINK,15=SOFT,16=BAD_SUB,17=UDF,18=DISABLE,19=SIMM,20=READ_ACCESS,21=WRITE_ACCESS").build())
            .field("severity", SchemaBuilder.int8().optional().doc("CA Alarm Severity: 0=NO_ALARM,1=MINOR,2=MAJOR,3=INVALID").build())
            .field("doubleValues", SchemaBuilder.array(Schema.OPTIONAL_FLOAT64_SCHEMA).optional().doc("EPICS DBR_DOUBLE").build())
            .field("floatValues", SchemaBuilder.array(Schema.OPTIONAL_FLOAT32_SCHEMA).optional().doc("EPICS DBR_FLOAT").build())
            .field("stringValues", SchemaBuilder.array(Schema.OPTIONAL_STRING_SCHEMA).optional().doc("EPICS DBR_STRING").build())
            .field("intValues", SchemaBuilder.array(Schema.OPTIONAL_INT32_SCHEMA).optional().doc("EPICS DBR_LONG; JCA refers to INT32 as DBR_INT; EPICS has no INT64").build())
            .field("shortValues", SchemaBuilder.array(Schema.OPTIONAL_INT16_SCHEMA).optional().doc("EPICS DBR_SHORT; DBR_INT is alias in EPICS (but not in JCA); Schema has no unsigned types so DBR_ENUM is also here").build())
            .field("byteValues", SchemaBuilder.array(Schema.OPTIONAL_INT8_SCHEMA).optional().doc("EPICS DBR_CHAR").build())
            .build();

    @After
    public void teardown() {
        xform.close();
    }


    @Test
    public void tombstoneSchemaless() {

        Map<String, Object> value = null;

        final SourceRecord record = new SourceRecord(null, null, null, null, null, null, value);
        final SourceRecord transformed = xform.apply(record);

        assertNull(transformed.value());
        assertNull(transformed.valueSchema());
    }

    @Test
    public void tombstoneWithSchema() {

        final Struct value = null;

        final SourceRecord record = new SourceRecord(null, null, null, null, null, INPUT_VALUE_SCHEMA, value);
        final SourceRecord transformed = xform.apply(record);

        assertNull(transformed.value());
        assertEquals(INPUT_VALUE_SCHEMA, transformed.valueSchema());
    }

    @Test
    public void schemaless() {
        Map<String, Object> value = new HashMap<>();

        value.put("severity", (byte)2);
        value.put("status", (byte)3);

        final SourceRecord record = new SourceRecord(null, null, null, null, null, null, value);
        final SourceRecord transformed = xform.apply(record);

        Map transformedValue = (Map)transformed.value();

        Map msg = (Map)transformedValue.get("msg");

        assertEquals(EpicsToAlarm.sevrByOrder[(byte)2].name(), ((Map)msg.get("org.jlab.jaws.entity.EPICSAlarming")).get("sevr"));
        assertEquals(EpicsToAlarm.statByOrder[(byte)3].name(), ((Map)msg.get("org.jlab.jaws.entity.EPICSAlarming")).get("stat"));
    }

    @Test
    public void withSchema() {
        final Struct value = new Struct(INPUT_VALUE_SCHEMA);

        value.put("severity", (byte)2);
        value.put("status", (byte)3);

        final SourceRecord record = new SourceRecord(null, null, null, null, null, INPUT_VALUE_SCHEMA, value);
        final SourceRecord transformed = xform.apply(record);

        Struct transformedValue = (Struct)transformed.value();

        Struct msg = transformedValue.getStruct("msg");

        assertEquals(EpicsToAlarm.sevrByOrder[(byte)2].name(), msg.getStruct("org.jlab.jaws.entity.EPICSAlarming").getString("sevr"));
        assertEquals(EpicsToAlarm.statByOrder[(byte)3].name(), msg.getStruct("org.jlab.jaws.entity.EPICSAlarming").getString("stat"));
    }

    @Test
    public void connectSchemaToAvroSchema() {
        AvroData outputData = new AvroData(new AvroDataConfig.Builder()
                .with(AvroDataConfig.ENHANCED_AVRO_SCHEMA_SUPPORT_CONFIG, true)
                .with(AvroDataConfig.CONNECT_META_DATA_CONFIG, false)
                .build());

        org.apache.kafka.connect.data.Schema connectSchema = EpicsToAlarm.updatedValueSchema;

        org.apache.avro.Schema actualAvroSchema = outputData.fromConnectSchema(connectSchema);

        org.apache.avro.Schema expectedAvroSchema = ActiveAlarm.getClassSchema();

        //System.out.println("Expected : " + expectedAvroSchema);
        //System.out.println("Actual   : " + actualAvroSchema);

        // Schema objects weirdly say they're equal even if doc fields are wrong so we use string comparison
        assertEquals(expectedAvroSchema.toString(), actualAvroSchema.toString());
    }
}

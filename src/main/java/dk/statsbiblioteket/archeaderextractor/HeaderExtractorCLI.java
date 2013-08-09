package dk.statsbiblioteket.archeaderextractor;

/*
 * #%L
 * ARC Header Extractor
 * %%
 * Copyright (C) 2013 State and University Library, Denmark
 * %%
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * #L%
 */


import org.jwat.arc.ArcReader;
import org.jwat.arc.ArcReaderFactory;
import org.jwat.arc.ArcRecordBase;
import org.jwat.common.*;

import java.io.*;
import java.util.HashMap;
import java.util.Map;

public class HeaderExtractorCLI {

    private static boolean hasPayload(ArcRecordBase arcRecord) throws IOException {
        Payload payload = arcRecord.getPayload();
        boolean result = payload != null;
        if (payload != null) {
            if (payload.getTotalLength() == 0) {
                result = false;
            }
            payload.close();
        }
        return result;
    }

    private static String extractHeader(ArcRecordBase arcRecord) throws IOException {
        StringWriter headerString = new StringWriter();

        HttpHeader httpHeader = null;
        if (hasPayload(arcRecord)) {
            httpHeader = arcRecord.getHttpHeader();
            if (httpHeader != null) {

                headerString.write("Offset: " + arcRecord.getStartOffset() + "\n");

                headerString.write("URL: " + arcRecord.getUrlStr() + "\n");
                headerString.write("IP:  " + arcRecord.getIpAddress() + "\n");

                headerString.write("ProtocolVersion: " + httpHeader.getProtocolVersion() + "\n");
                headerString.write("ProtocolStatusCode: " + httpHeader.getProtocolStatusCodeStr() + "\n");
                headerString.write("ProtocolContentType: " + httpHeader.getProtocolContentType() + "\n");
                headerString.write("TotalLength: " + httpHeader.getTotalLength() + "\n");

                for (HeaderLine hl : httpHeader.getHeaderList()) {
                    headerString.write(hl.name + ": " + hl.value + "\n");
                }
            }
        }
        if (httpHeader != null) {
            httpHeader.close();
        }
        arcRecord.close();
        return headerString.toString();
    }

    private static Map<Long, String> parse(File file) throws IOException {
        Map<Long, String> headers = new HashMap<Long, String>(); // return instance for this method

        RandomAccessFile raf;
        RandomAccessFileInputStream rafin;
        ByteCountingPushBackInputStream pbin;

        ArcReader arcReader = null;
        ArcRecordBase arcRecord;
        UriProfile uriProfile = UriProfile.RFC3986;

        boolean bBlockDigestEnabled = true;
        boolean bPayloadDigestEnabled = true;
        int recordHeaderMaxSize = 8192;
        int payloadHeaderMaxSize = 32768;

        raf = new RandomAccessFile(file, "r");
        rafin = new RandomAccessFileInputStream(raf);
        pbin = new ByteCountingPushBackInputStream(new BufferedInputStream(rafin, 8192), 16);

        if (ArcReaderFactory.isArcFile(pbin)) {
            arcReader = ArcReaderFactory.getReaderUncompressed(pbin);
            arcReader.setUriProfile(uriProfile);
            arcReader.setBlockDigestEnabled(bBlockDigestEnabled);
            arcReader.setPayloadDigestEnabled(bPayloadDigestEnabled);
            arcReader.setRecordHeaderMaxSize(recordHeaderMaxSize);
            arcReader.setPayloadHeaderMaxSize(payloadHeaderMaxSize);

            while ((arcRecord = arcReader.getNextRecord()) != null) {
                headers.put(arcRecord.getStartOffset(), extractHeader(arcRecord));
            }
            arcReader.close();
        } else {
            System.err.println("Input file is not an ARC file");
        }

        if (arcReader != null) {
            arcReader.close();
        }
        pbin.close();
        raf.close();

        return headers;
    }

    /**
     * main class called from the CLI with two parameters:
     *
     * @param args contains two elements:
     *             0: a file name or a directory name. In the latter case all files ending with .arc will
     *             be included in the job.
     *             1: a full path to a directory where the output files will be placed.
     */
    public static void main(String[] args) {

        File inputFile = new File(args[0]);
        File outputDirectory = new File(args[1]);
        try {
            if (!inputFile.isFile() || !outputDirectory.isDirectory()) {
                System.out.println("The first argument must be a file and the second a directory.");
                System.exit(0);
            }
        } catch (Exception e) {
            System.out.println("Unable to read input.");
            System.out.println(e);
            System.exit(1);
        }

        Map<Long, String> allHeaders = null;
        try {
            allHeaders = parse(inputFile);
        } catch (IOException e) {
            System.err.println("Unable to parse the ARC file.");
            e.printStackTrace();
        }
        if (allHeaders != null) {
            System.out.println("ARC file analysed and " + allHeaders.size() + " headers were found.");

            try {

                for (Map.Entry<Long, String> entry : allHeaders.entrySet()) {
                    Long key = entry.getKey();
                    String value = entry.getValue();

                    if (value.length() != 0) {
                        FileWriter headerFile;
                        headerFile = new FileWriter(
                                outputDirectory.getAbsolutePath() + "/"
                                        + inputFile.getName()
                                        + "-"
                                        + key);
                        headerFile.write(value + "Filename: " + inputFile.getName());
                        headerFile.close();
                    }

                }

            } catch (IOException e) {
                System.err.println("Unable to read and write date.");
                e.printStackTrace();
            }
        } else {
            System.err.println("No records were found in the ARC file!");
        }
        System.exit(0);
    }
}

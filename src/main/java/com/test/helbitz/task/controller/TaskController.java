package com.test.helbitz.task.controller;

import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.Phonenumber;
import com.test.helbitz.task.model.ReportResponse;
import com.test.helbitz.task.model.api.CountryResponse;
import com.test.helbitz.task.model.api.FbiResponse;
import com.test.helbitz.task.model.ReportRequest;
import com.test.helbitz.task.model.api.FbiResponseItem;
import com.test.helbitz.task.util.HelbitzTaskConstants;
import jdk.nashorn.internal.runtime.arrays.ArrayLikeIterator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.*;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

@Slf4j
@Controller
@RequestMapping("/witness")
public class TaskController {

    @Autowired
    private RestTemplate restTemplate;

    @PostMapping("/report")
    @ResponseBody
    public ResponseEntity<ReportResponse> postReport(@RequestBody ReportRequest reportRequest){
        ReportResponse reportResponse = new ReportResponse();
        HttpStatus httpStatus = null;

        if( !isValidPhone( reportRequest.getPhoneNumber() ) ){
            reportResponse.setCode(HelbitzTaskConstants.ERR_PHONE);
            reportResponse.setMessage(HelbitzTaskConstants.MSG_ERR_PHONE);

            httpStatus = HttpStatus.BAD_REQUEST;

            log.warn("Request sent with invalid phone number " + reportRequest.getPhoneNumber());
        }
        else if(!isInFbiDb(reportRequest.getName()) && !(checkFbiDb(reportRequest.getName()) >0)){
            reportResponse.setCode(HelbitzTaskConstants.ERR_PERSON);
            reportResponse.setMessage(HelbitzTaskConstants.MSG_ERR_PERSON);

            httpStatus = HttpStatus.NOT_FOUND;

            log.info("Reported person does not exist in database");
        }
        else{
            //Write in file
            try{
                String content = Calendar.getInstance().getTime().toString()
                        + " witness report from location: "
                        + getCountryByCode(countryCodeFromPhone(reportRequest.getPhoneNumber())) + ", "
                        + reportRequest.getName() + " spotted.";

                if(isInFbiDb(reportRequest.getName())) {
                    content += " NOTE:( exact match )\n";
                }
                else if(checkFbiDb(reportRequest.getName()) > 1){
                    content += " NOTE:( multiple matches found "+ checkFbiDb(reportRequest.getName()) +")\n";
                }
                else{
                    content += " NOTE: ( name partially match or in different arrangement )\n";
                }

                Path path = Paths.get("./output_log.txt");
                if(Files.exists(path)){
                    Files.write(path, content.getBytes(StandardCharsets.UTF_8), StandardOpenOption.APPEND);
                }
                else{
                    Files.write(path, content.getBytes(StandardCharsets.UTF_8));
                }

                reportResponse.setCode(HelbitzTaskConstants.SUCCESS);
                reportResponse.setMessage(HelbitzTaskConstants.MSG_SUCCESS);

                httpStatus = HttpStatus.OK;

                log.info("New report succesfully posted");
            }
            catch (IOException e){
                //e.printStackTrace();
                reportResponse.setCode(HelbitzTaskConstants.ERR);
                reportResponse.setMessage(HelbitzTaskConstants.MSG_ERR);

                httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
                //return new ResponseEntity<ReportResponse>(reportResponse, httpStatus);
                log.error("Error occurred while writing/appending file");
            }


        }
        return new ResponseEntity<ReportResponse>(reportResponse, httpStatus);
    }





    //Check if name matches with FBI database title or aliases
    private int checkFbiDb(String name){
        String endpoint = "https://api.fbi.gov/wanted/v1/list?title="+name;

        HttpHeaders headers = new HttpHeaders();
        headers.add("user-agent", "Application");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        FbiResponse res = restTemplate.exchange(endpoint, HttpMethod.GET, entity, FbiResponse.class).getBody();

        int numMatches = 0;

        for(FbiResponseItem fbiItem: res.getItems()){
            String dbName = fbiItem.getTitle().toUpperCase(Locale.ROOT);
            String reqName = name.toUpperCase(Locale.ROOT);

            List<String> nameList = Arrays.asList(reqName.split(" "));
            List<String> titleList = Arrays.asList(dbName.split(" "));

            if(titleList.containsAll(nameList)){
                numMatches++;
            }
            else if(fbiItem.getAliases() != null){
              for(String alias : fbiItem.getAliases()){
                  String dbAlias = alias.toUpperCase(Locale.ROOT);
                  List<String> aliasList = Arrays.asList(dbAlias.split(" "));

                  if(aliasList.containsAll(nameList)){
                      numMatches++;
                      break;
                  }
              }
            }
        }
        return numMatches;
    }

    //Check if name has exact match in title
    private boolean isInFbiDb(String name){
        String endpoint = "https://api.fbi.gov/wanted/v1/list?title="+name;

        HttpHeaders headers = new HttpHeaders();
        headers.add("user-agent", "Application");

        HttpEntity<String> entity = new HttpEntity<>(headers);

        FbiResponse res = restTemplate.exchange(endpoint, HttpMethod.GET, entity, FbiResponse.class).getBody();

        for(int i=0; i < res.getItems().size(); i++){
            String dbTitle = res.getItems().get(i).getTitle().toUpperCase(Locale.ROOT);

            if( dbTitle.equals(name.toUpperCase(Locale.ROOT)))
                return true;
        }

        return false;
    }

    //Check if contact phone is valid
    private boolean isValidPhone(String phone){
        PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
        Phonenumber.PhoneNumber phoneNumber = null;

        try {
            phoneNumber = phoneUtil.parse(phone, "IN");
            return true;
        }
        catch (NumberParseException e) {
            //e.printStackTrace();
            log.warn(
                    "Unable to parse the given phone number: "
                    + phone + e.getLocalizedMessage());

            return false;
        }
    }

    //Get country code from phone
    private String countryCodeFromPhone(String phone){
        PhoneNumberUtil phoneUtil = PhoneNumberUtil.getInstance();
        Phonenumber.PhoneNumber phoneNumber = null;

        try {
            phoneNumber = phoneUtil.parse(phone, "IN");
            return phoneUtil.getRegionCodeForNumber(phoneNumber);
        }
        catch (NumberParseException e) {
            //e.printStackTrace();
            log.error(
                    "Unable to parse the given phone number: "
                            + phone + e.getLocalizedMessage());

            return "XXX";
        }
    }

    //Country by code
    private String getCountryByCode(String code){
        String endpoint = "https://restcountries.com/v2/alpha/"+code;

        try{
            HttpHeaders headers = new HttpHeaders();
            headers.add("user-agent", "Application");

            HttpEntity<String> entity = new HttpEntity<>(headers);

            CountryResponse res = restTemplate.exchange(endpoint, HttpMethod.GET, entity, CountryResponse.class).getBody();


            return res.getName();
        }
        catch(Exception e){
            log.error("Unable to find country for given county code :"+code);
            return "NOT FOUND";
        }
    }
}

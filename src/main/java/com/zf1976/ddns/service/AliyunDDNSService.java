package com.zf1976.ddns.service;

import com.aliyuncs.AcsRequest;
import com.aliyuncs.AcsResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.alidns.model.v20150109.*;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.profile.DefaultProfile;
import com.zf1976.ddns.property.AliyunDnsProperties;
import com.zf1976.ddns.util.HttpUtil;
import com.zf1976.ddns.util.ObjectUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 暂时只支持A记录类型解析
 * @author mac
 * @date 2021/7/6
 */
@SuppressWarnings({"FieldCanBeLocal", "UnusedReturnValue"})
public class AliyunDDNSService {

    private final Logger log = LoggerFactory.getLogger("[AliyunDDNSApi]");
    private final IAcsClient iAcsClient;
    private final AliyunDnsProperties properties;
    private DescribeDomainRecordsRequest describeDomainRecordsRequest = new DescribeDomainRecordsRequest();
    private UpdateDomainRecordRequest updateDomainRecordRequest = new UpdateDomainRecordRequest();
    private AddDomainRecordRequest addDomainRecordRequest = new AddDomainRecordRequest();
    private static final String RECORD_TYPE = "A";

    public AliyunDDNSService(AliyunDnsProperties properties) {
        this.properties = properties;
        // 设置鉴权参数，初始化客户端
        final var profile = DefaultProfile.getProfile(
                this.properties.getDefaultRegionId(),
                this.properties.getAccessKeyId(),
                this.properties.getSecret()
        );
        this.iAcsClient = new DefaultAcsClient(profile);
    }

    /**
     * 获取域名的所有解析记录列表
     */
    public DescribeDomainRecordsResponse findDescribeDomainRecords(){
        return this.getAcsResponse(this.describeDomainRecordsRequest);
    }

    /**
     * 根据域名的所有解析记录列表
     *
     * @param domain 域名
     * @return {@link DescribeDomainRecordsResponse}
     */
    public DescribeDomainRecordsResponse findDescribeDomainRecords(String domain) {
        if (ObjectUtil.isEmpty(this.describeDomainRecordsRequest)) {
            throw new RuntimeException("describeDomainRecordsRequest cannot been null");
        }
        final var domainRecord = this.getDomainRecord(domain);
        this.describeDomainRecordsRequest.setDomainName(domainRecord[0]);
        this.describeDomainRecordsRequest.setRRKeyWord(domainRecord[1]);
        this.describeDomainRecordsRequest.setType(RECORD_TYPE);
        return this.getAcsResponse(this.describeDomainRecordsRequest);
    }

    /**
     * 修改解析记录
     */
    public UpdateDomainRecordResponse updateDomainRecord(){
        return this.getAcsResponse(this.updateDomainRecordRequest);
    }

    /**
     * 根据解析记录id更新记录值
     *
     * @param recordId 记录id
     * @param record 记录
     * @param ip ip
     * @return {@link UpdateDomainRecordResponse}
     */
    public UpdateDomainRecordResponse updateDomainRecord(String recordId, String record, String ip) {
        if (ObjectUtil.isEmpty(this.updateDomainRecordRequest)) {
            throw new RuntimeException("updateDomainRecordRequest cannot been null");
        }
        this.checkIp(ip);
        this.updateDomainRecordRequest.setRecordId(recordId);
        this.updateDomainRecordRequest.setRR(record);
        this.updateDomainRecordRequest.setValue(ip);
        this.updateDomainRecordRequest.setType(RECORD_TYPE);
        return this.getAcsResponse(this.updateDomainRecordRequest);
    }

    /**
     * 新增解析记录
     */
    public AddDomainRecordResponse addDomainRecord() {
        return this.getAcsResponse(this.addDomainRecordRequest);
    }

    /**
     * 新增域名指向ip
     *
     * @param domain 域名
     * @param ip ip
     * @return {@link AddDomainRecordResponse}
     */
    public AddDomainRecordResponse addDomainRecordResponse(String domain, String ip) {
        if (ObjectUtil.isEmpty(this.addDomainRecordRequest)) {
            throw new RuntimeException("addDomainRecordRequest cannot been null");
        }
        this.checkIp(ip);
        final var domainRecord = this.getDomainRecord(domain);
        this.addDomainRecordRequest.setDomainName(domainRecord[0]);
        this.addDomainRecordRequest.setRR(domainRecord[1]);
        this.addDomainRecordRequest.setValue(ip);
        this.addDomainRecordRequest.setType(RECORD_TYPE);
        return this.getAcsResponse(addDomainRecordRequest);
    }

    public <T extends AcsResponse> T getAcsResponse(AcsRequest<T> request) {
        try {
            // 调用SDK发送请求
            return this.iAcsClient.getAcsResponse(request);
        } catch (ClientException e) {
            log.error(e.getMessage(), e.getCause());
            // 发生调用错误，抛出运行时异常
            throw new RuntimeException(e);
        }
    }

    private String[] getDomainRecord(String domain) {
        final var extractDomain = HttpUtil.extractDomain(domain);
        if (ObjectUtil.isEmpty(extractDomain)) {
            throw new RuntimeException("Please fill in the correct domain name");
        }
        return extractDomain;
    }

    private void checkIp(String ip) {
        if (!HttpUtil.isIp(ip)) {
            throw new RuntimeException("String: "+ ip +" is not a correct ip");
        }
    }

    public void setDescribeDomainRecordsRequest(DescribeDomainRecordsRequest describeDomainRecordsRequest) {
        this.describeDomainRecordsRequest = describeDomainRecordsRequest;
    }

    public void setUpdateDomainRecordRequest(UpdateDomainRecordRequest updateDomainRecordRequest) {
        this.updateDomainRecordRequest = updateDomainRecordRequest;
    }

    public AliyunDDNSService setAddDomainRecordRequest(AddDomainRecordRequest addDomainRecordRequest) {
        this.addDomainRecordRequest = addDomainRecordRequest;
        return this;
    }

    public DescribeDomainRecordsRequest getDescribeDomainRecordsRequest() {
        return describeDomainRecordsRequest;
    }

    public UpdateDomainRecordRequest getUpdateDomainRecordRequest() {
        return updateDomainRecordRequest;
    }

    public AddDomainRecordRequest getAddDomainRecordRequest() {
        return addDomainRecordRequest;
    }
}
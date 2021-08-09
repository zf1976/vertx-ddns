package com.zf1976.ddns.verticle.timer;

import com.zf1976.ddns.api.enums.DnsSRecordType;
import com.zf1976.ddns.api.provider.AliyunDnsRecordProvider;
import com.zf1976.ddns.api.provider.CloudflareDnsRecordProvider;
import com.zf1976.ddns.api.provider.DnspodDnsRecordProvider;
import com.zf1976.ddns.api.provider.HuaweiDnsProvider;
import com.zf1976.ddns.api.provider.DnsRecordProvider;
import com.zf1976.ddns.pojo.*;
import com.zf1976.ddns.pojo.vo.DnsRecordVo;
import com.zf1976.ddns.util.CollectionUtil;
import com.zf1976.ddns.util.HttpUtil;
import com.zf1976.ddns.util.LogUtil;
import com.zf1976.ddns.verticle.DNSServiceType;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.*;

/**
 * @author ant
 * Create by Ant on 2021/7/28 10:01 下午
 */
@SuppressWarnings("rawtypes")
public class DnsConfigTimerService extends AbstractDnsRecordHandler{

    private final Logger log = LogManager.getLogger("[DnsConfigTimerService]");

    public DnsConfigTimerService(List<DDNSConfig> ddnsConfigList, Vertx vertx) {
        super(ddnsConfigList, vertx);
    }

    public List<DnsRecordVo> findDnsRecords(DNSServiceType dnsServiceType, String domain, DnsSRecordType dnsRecordType) {
        final var api = this.dnsApiMap.get(dnsServiceType);
        try {
            if (api != null && api.support(dnsServiceType)) {
                final var result = api.findDnsRecordList(domain, dnsRecordType);
                return this.handlerGenericsResult(result, domain);
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e.getCause());
            throw new RuntimeException(e.getMessage(), e.getCause());
        }
        throw new RuntimeException("No service provider key configured");
    }

    @SuppressWarnings("unchecked")
    public Future<List<DnsRecordVo>> findDnsRecordListAsync(DNSServiceType dnsServiceType,
                                                            String domain,
                                                            DnsSRecordType dnsRecordType) {
        return Future.succeededFuture(this.dnsApiMap.get(dnsServiceType))
                     .compose(api -> {
                         if (api == null) {
                             return Future.failedFuture("No service provider");
                         }
                         return api.supportAsync(dnsServiceType)
                                   .compose(v -> api.findDnsRecordListAsync(domain, dnsRecordType));
                     })
                     .compose(result -> {
                         final var dnsRecordVoList = this.handlerGenericsResult(result, domain);
                         return Future.succeededFuture(dnsRecordVoList);
                     });

    }

    public Boolean deleteRecord(DNSServiceType dnsServiceType, String recordId, String domain) {
        final var api = this.dnsApiMap.get(dnsServiceType);
        if (api == null) {
            throw new RuntimeException("No service provider");
        }
        try {
            if (api.support(dnsServiceType)) {
                if (api instanceof DnspodDnsRecordProvider) {
                    final var dnspodDataResult = (DnspodDataResult) api.deleteDnsRecord(recordId, domain);
                    return dnspodDataResult != null && dnspodDataResult.getResponse()
                                                                       .getError() == null;
                }
                if (api instanceof AliyunDnsRecordProvider || api instanceof HuaweiDnsProvider) {
                    return api.deleteDnsRecord(recordId, domain) != null;
                }
                if (api instanceof CloudflareDnsRecordProvider) {
                    return ((CloudflareDataResult) api.deleteDnsRecord(recordId, domain)).getSuccess();
                }
            }
        } catch (Exception e) {
            LogUtil.printDebug(log, e.getMessage(), e.getCause());
            throw new RuntimeException(e);
        }
        return Boolean.FALSE;
    }

    @SuppressWarnings("unchecked")
    public Future<Boolean> deleteRecordAsync(DNSServiceType dnsServiceType, String recordId, String domain) {
        final var api = this.dnsApiMap.get(dnsServiceType);
        return Future.succeededFuture(api)
                     .compose(checkApi -> {
                         if (checkApi == null) {
                             return Future.failedFuture("No service provider");
                         }
                         return checkApi.supportAsync(dnsServiceType)
                                        .compose(v -> checkApi.deleteDnsRecordAsync(recordId, domain));
                     })
                     .compose(result -> this.futureDeleteResultHandler(api, result));

    }

    private Future<Boolean> futureDeleteResultHandler(DnsRecordProvider api, Object result) {
        boolean complete = Boolean.FALSE;
        if (api instanceof DnspodDnsRecordProvider) {
            final var dnspodDataResult = (DnspodDataResult) result;
            complete = dnspodDataResult != null && dnspodDataResult.getResponse()
                                                                   .getError() == null;
        }
        if (api instanceof AliyunDnsRecordProvider || api instanceof HuaweiDnsProvider) {
            complete = result != null;
        }
        if (api instanceof CloudflareDnsRecordProvider) {
            complete = ((CloudflareDataResult) result).getSuccess();
        }
        return Future.succeededFuture(complete);
    }

    private List<DnsRecordVo> handlerGenericsResult(Object obj, String domain) {
        List<DnsRecordVo> recordVoList = new LinkedList<>();
        if (obj instanceof AliyunDataResult result && result.getDomainRecords() != null) {
            final var domainRecords = result.getDomainRecords()
                                            .getRecordList();
            for (AliyunDataResult.Record record : domainRecords) {
                final var recordVo = DnsRecordVo.newBuilder()
                                                .withId(record.getRecordId())
                                                .withDomain(record.getDomainName())
                                                .withRr(record.getRr())
                                                .withValue(record.getValue())
                                                .build();
                recordVoList.add(recordVo);
            }
        }

        if (obj instanceof DnspodDataResult result && result.getResponse() != null) {
            final var recordList = result.getResponse()
                                         .getRecordList();
            if (!CollectionUtil.isEmpty(recordList)) {
                for (DnspodDataResult.RecordList record : recordList) {
                    final var extractDomain = HttpUtil.extractDomain(domain);
                    final var recordVo = DnsRecordVo.newBuilder()
                                                    .withId(String.valueOf(record.getRecordId()))
                                                    .withDomain(extractDomain[0])
                                                    .withRr(record.getName())
                                                    .withValue(record.getValue())
                                                    .build();
                    recordVoList.add(recordVo);
                }
            }
        }

        if (obj instanceof HuaweiDataResult result) {
            final var recordList = result.getRecordsets();
            if (!CollectionUtil.isEmpty(recordList)) {
                for (HuaweiDataResult.Recordsets record : recordList) {
                    final var huaweiDomain = record.getName()
                                                   .substring(0, record.getName()
                                                                       .length() - 1);
                    final var extractDomain = HttpUtil.extractDomain(huaweiDomain);
                    final var recordVo = DnsRecordVo.newBuilder()
                                                    .withId(record.getId())
                                                    .withDomain(extractDomain[0])
                                                    .withRr(Objects.equals(extractDomain[1], "") ? "@" : extractDomain[1])
                                                    .withValue(CollectionUtil.isEmpty(record.getRecords()) ? null : record.getRecords()
                                                                                                                          .get(0))
                                                    .build();
                    recordVoList.add(recordVo);
                }
            }
        }

        if (obj instanceof CloudflareDataResult result && result.getSuccess()) {
            final var resultList = result.getResult();
            if (!CollectionUtil.isEmpty(resultList)) {
                for (CloudflareDataResult.Result record : resultList) {
                    final var extractDomain = HttpUtil.extractDomain(record.getName());
                    final var recordVo = DnsRecordVo.newBuilder()
                                                    .withId(record.getId())
                                                    .withDomain(record.getZoneName())
                                                    .withRr(Objects.equals(extractDomain[1], "") ? "@" : extractDomain[1])
                                                    .withValue(record.getContent())
                                                    .build();
                    recordVoList.add(recordVo);
                }
            }
        }
        return recordVoList;
    }

}

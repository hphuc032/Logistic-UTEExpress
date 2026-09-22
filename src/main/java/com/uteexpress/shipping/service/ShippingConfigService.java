package com.uteexpress.shipping.service;

import com.uteexpress.common.exception.*;
import com.uteexpress.governance.dto.AuditEntry;
import com.uteexpress.governance.service.AuditLogService;
import com.uteexpress.security.service.CurrentAccountIdProvider;
import com.uteexpress.shipping.dto.*;
import com.uteexpress.shipping.entity.*;
import com.uteexpress.shipping.repository.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.data.domain.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;
import java.util.*;
import java.util.function.Supplier;

@Service @Validated
@PreAuthorize("hasAnyAuthority(T(com.uteexpress.security.RoleCode).ADMIN.authority(), T(com.uteexpress.security.RoleCode).MANAGER.authority())")
public class ShippingConfigService {
    private final ProviderRepository providers;
    private final ShippingRateRepository rates;
    private final AuditLogService audit;
    private final CurrentAccountIdProvider accounts;
    public ShippingConfigService(ProviderRepository providers, ShippingRateRepository rates,
            AuditLogService audit, CurrentAccountIdProvider accounts) {
        this.providers=providers; this.rates=rates; this.audit=audit; this.accounts=accounts;
    }
    @Transactional(readOnly=true)
    public Page<ShippingProviderView> providers(int page) { return providers.findAll(page(page)).map(ShippingConfigService::view); }
    @Transactional(readOnly=true)
    public List<ShippingProviderView> activeProviders() { return providers.findByActiveTrueOrderByNameAsc().stream().map(ShippingConfigService::view).toList(); }
    @Transactional(readOnly=true)
    public Page<ShippingRateView> rates(int page) { return rates.findAll(page(page)).map(ShippingConfigService::view); }
    @Transactional(readOnly=true)
    public ShippingProviderView provider(Long id) { return view(requireProvider(id)); }
    @Transactional(readOnly=true)
    public ShippingRateView rate(Long id) { return view(requireRate(id)); }

    @Transactional
    public ShippingProviderView createProvider(@NotNull @Valid ShippingProviderRequest request) {
        Long actor=actor();
        var provider=new ShippingProvider(request.code(),request.name());
        provider.update(request.name(),request.active());
        save(() -> providers.saveAndFlush(provider));
        log(actor,"PROVIDER_CREATED","SHIPPING_PROVIDER",provider.getId(),Map.of(),state(provider));
        return view(provider);
    }
    @Transactional
    public void updateProvider(Long id,@NotNull @Valid ShippingProviderRequest request) {
        Long actor=actor();
        var provider=requireProvider(id);
        version(provider.getVersion(),request.version());
        if (!provider.getCode().equals(request.code())) throw invalid();
        var before=state(provider);
        provider.update(request.name(),request.active());
        save(() -> providers.saveAndFlush(provider));
        log(actor,"PROVIDER_UPDATED","SHIPPING_PROVIDER",id,before,state(provider));
    }
    @Transactional
    public void disableProvider(Long id,Long expectedVersion) {
        Long actor=actor();
        var provider=requireProvider(id);
        version(provider.getVersion(),expectedVersion);
        if (!provider.isActive()) return;
        var before=state(provider);
        provider.update(provider.getName(),false);
        save(() -> providers.saveAndFlush(provider));
        log(actor,"PROVIDER_DISABLED","SHIPPING_PROVIDER",id,before,state(provider));
    }
    @Transactional
    public ShippingRateView createRate(@NotNull @Valid ShippingRateRequest request) {
        Long actor=actor();
        var provider=requireProvider(request.providerId());
        if (!provider.isActive()) throw invalid();
        var rate=new ShippingRate(provider,request.serviceCode(),request.destinationRegion(),request.fee().setScale(2));
        rate.update(request.fee().setScale(2),request.active());
        save(() -> rates.saveAndFlush(rate));
        log(actor,"RATE_CREATED","SHIPPING_RATE",rate.getId(),Map.of(),state(rate));
        return view(rate);
    }
    @Transactional
    public void updateRate(Long id,@NotNull @Valid ShippingRateRequest request) {
        Long actor=actor();
        var rate=requireRate(id);
        version(rate.getVersion(),request.version());
        if (!rate.getProvider().getId().equals(request.providerId())
                || !rate.getServiceCode().equals(request.serviceCode())
                || !rate.getDestinationRegion().equals(request.destinationRegion())) throw invalid();
        if (request.active() && !rate.getProvider().isActive()) throw invalid();
        var before=state(rate);
        rate.update(request.fee().setScale(2),request.active());
        save(() -> rates.saveAndFlush(rate));
        log(actor,"RATE_UPDATED","SHIPPING_RATE",id,before,state(rate));
    }
    @Transactional
    public void disableRate(Long id,Long expectedVersion) {
        Long actor=actor();
        var rate=requireRate(id);
        version(rate.getVersion(),expectedVersion);
        if (!rate.isActive()) return;
        var before=state(rate);
        rate.update(rate.getFee(),false);
        save(() -> rates.saveAndFlush(rate));
        log(actor,"RATE_DISABLED","SHIPPING_RATE",id,before,state(rate));
    }
    private Long actor() { return accounts.currentAccountId().filter(id -> id>0).orElseThrow(() -> new ApplicationException(ErrorCode.UNAUTHENTICATED)); }
    private ShippingProvider requireProvider(Long id) {
        if(id == null || id<=0) throw invalid();
        return providers.findById(id).orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
    }
    private ShippingRate requireRate(Long id) {
        if(id == null || id<=0) throw invalid();
        return rates.findById(id).orElseThrow(() -> new ApplicationException(ErrorCode.RESOURCE_NOT_FOUND));
    }
    private static void version(Long actual,Long expected) {
        if(expected == null || expected<0) throw invalid();
        if(!actual.equals(expected)) throw new ApplicationException(ErrorCode.CONFLICT);
    }
    private static Pageable page(int page) {
        if(page<0 || page>100000) throw invalid();
        return PageRequest.of(page,20,Sort.by("id").descending());
    }
    private static <T> T save(Supplier<T> action) {
        try { return action.get(); }
        catch(DataIntegrityViolationException | ObjectOptimisticLockingFailureException e) {
            throw new ApplicationException(ErrorCode.CONFLICT);
        }
    }
    private void log(Long actor,String action,String type,Long id,Map<String,String> before,Map<String,String> after) {
        audit.append(new AuditEntry(actor,action,type,id,before,after,"SHIPPING_CONFIGURATION"));
    }
    private static Map<String,String> state(ShippingProvider p) {
        return Map.of("active",Boolean.toString(p.isActive()),"version",p.getVersion().toString());
    }
    private static Map<String,String> state(ShippingRate r) {
        return Map.of("active",Boolean.toString(r.isActive()),"version",r.getVersion().toString(),"relatedId",r.getProvider().getId().toString());
    }
    private static ShippingProviderView view(ShippingProvider p) { return new ShippingProviderView(p.getId(),p.getCode(),p.getName(),p.isActive(),p.getVersion()); }
    private static ShippingRateView view(ShippingRate r) {
        return new ShippingRateView(r.getId(),r.getProvider().getId(),r.getProvider().getName(),
                r.getServiceCode(),r.getDestinationRegion(),r.getFee(),r.isActive(),r.getVersion());
    }
    private static ApplicationException invalid() { return new ApplicationException(ErrorCode.VALIDATION_FAILED); }
}

import { HttpErrorResponse } from '@angular/common/http';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { of, throwError } from 'rxjs';
import { radioCleanGuard } from '../../core/tenant/radio-clean.guard';
import { RadioRoutingService } from './radio-routing.service';
import { StorefrontApiService } from '../storefront/storefront-api.service';
import { StorefrontRoutingService } from '../storefront/storefront-routing.service';
@Component({selector:'test-radio', template:'RADIO'}) class RadioStub {}
@Component({selector:'test-missing', template:'404'}) class MissingStub {}
describe('clean RADIO URLs', () => {
 const api = { getSettings: vi.fn() };
 beforeEach(() => {
  api.getSettings.mockReset().mockReturnValue(of({tenantType:'RADIO'}));
  TestBed.configureTestingModule({providers:[{provide:StorefrontApiService,useValue:api},
   {provide:StorefrontRoutingService,useValue:{route:()=>['/tiendas']}},
   provideRouter([{path:'no-encontrado',component:MissingStub},{path:':storeSlug',canMatch:[radioCleanGuard],children:[{path:'**',component:RadioStub}]},{path:'**',component:MissingStub}]) ]});
 });
 it.each(['','programas','nosotros','socios','login','registro','mi-cuenta','mi-cuenta/pago-retorno'])('matches RADIO deep link %s', async suffix => {
  const h=await RouterTestingHarness.create(); await h.navigateByUrl('/atodoboca/'+suffix,RadioStub);
  expect(api.getSettings).toHaveBeenCalledWith('atodoboca');
 });
 it.each(['admin','superadmin','api','tiendas','assets'])('excludes reserved %s',async slug=>{
  const h=await RouterTestingHarness.create();await h.navigateByUrl('/'+slug,MissingStub);expect(api.getSettings).not.toHaveBeenCalled();
 });
 it('rejects ecommerce',async()=>{api.getSettings.mockReturnValue(of({tenantType:'ECOMMERCE'})); const h=await RouterTestingHarness.create();await h.navigateByUrl('/shop',MissingStub);});
 it('renders missing tenants without technical details',async()=>{api.getSettings.mockReturnValue(throwError(()=>new HttpErrorResponse({status:404})));const h=await RouterTestingHarness.create();await h.navigateByUrl('/missing',MissingStub);expect(TestBed.inject(Router).url).toBe('/no-encontrado');});
 it('centralizes tenant links and the login alias',()=>{ const r=TestBed.inject(RadioRoutingService);expect(r.route('radio-a','ingresar')).toEqual(['/','radio-a','login']);expect(r.route('radio-b','mi-cuenta','pago-retorno')).toEqual(['/','radio-b','mi-cuenta','pago-retorno']);});
});

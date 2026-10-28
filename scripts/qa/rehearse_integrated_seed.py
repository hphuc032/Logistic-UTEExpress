"""Explicit disposable QA rehearsal, never a production seed or browser acceptance."""
import argparse, base64, datetime as dt, email, email.utils, hashlib, http.cookiejar
import json, os, pathlib, re, secrets, socket, socketserver, struct, subprocess, shutil
import threading, time, urllib.error, urllib.parse, urllib.request, uuid, zipfile, zlib

class SeedError(Exception): pass

def require(ok, code):
    if not ok: raise SeedError(code)

def image():
    def chunk(t, d): return struct.pack('!I',len(d))+t+d+struct.pack('!I',zlib.crc32(t+d)&0xffffffff)
    return b'\x89PNG\r\n\x1a\n'+chunk(b'IHDR',struct.pack('!2I5B',2,2,8,2,0,0,0))+chunk(b'IDAT',zlib.compress(b'\0'+b'\xff\0\0'*2+b'\0'+b'\0\xff\0'*2))+chunk(b'IEND',b'')

class MailHandler(socketserver.StreamRequestHandler):
    def handle(self):
        self.connection.settimeout(15); self.wfile.write(b'220 localhost QA SMTP\r\n')
        while True:
            line=self.rfile.readline(16384)
            if not line: return
            command=line.split(b' ',1)[0].strip().upper()
            if command in (b'EHLO',b'HELO'): self.wfile.write(b'250-localhost\r\n250 8BITMIME\r\n')
            elif command==b'DATA':
                self.wfile.write(b'354 End with dot\r\n'); body=bytearray()
                while True:
                    part=self.rfile.readline(16384)
                    if part in (b'.\r\n',b'.\n',b''): break
                    body.extend(part[1:] if part.startswith(b'..') else part)
                    require(len(body)<65536,'SMTP_FIXTURE_TOO_LARGE')
                message=email.message_from_bytes(bytes(body)); payload=message.get_payload(decode=True) or b''
                code=re.search(rb'\b[0-9]{6}\b',payload)
                if code:
                    for _,address in email.utils.getaddresses(message.get_all('To',[])):
                        with self.server.mail_lock: self.server.codes[address.lower()]=code.group().decode()
                self.wfile.write(b'250 Accepted\r\n')
            elif command==b'QUIT': self.wfile.write(b'221 Bye\r\n'); return
            else: self.wfile.write(b'250 OK\r\n')

class Client:
    def __init__(self, seed):
        self.seed=seed; self.opener=urllib.request.build_opener(urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()))
    def call(self,path,data=None,headers=None,allowed=(200,201,204)):
        request=urllib.request.Request(self.seed.base+path,data=data,headers=headers or {})
        try:
            with self.opener.open(request,timeout=20) as response: status=response.status; body=response.read()
        except urllib.error.HTTPError as error: status=error.code; body=error.read()
        event={'method':'POST' if data is not None else 'GET','path':path,'status':status}
        if status not in allowed:
            try:
                detail=json.loads(body);event['error_code']=detail.get('code');event['violation_fields']=[v.get('field') for v in detail.get('violations',[])]
            except (ValueError,TypeError):pass
            (self.seed.out/'last-http-response.private').write_bytes(body)
        self.seed.http.append(event)
        require(status in allowed,'HTTP_'+str(status)+'_'+path)
        return body
    def token(self):
        page=self.call('/login').decode(); match=re.search(r'name="_csrf"[^>]*value="([^"]+)"',page)
        require(match is not None,'CSRF_FIELD_MISSING'); return match.group(1)
    def form(self,path,values):
        body=self.call(path,urllib.parse.urlencode(dict(values,_csrf=self.token())).encode(),{'Content-Type':'application/x-www-form-urlencoded'})
        if path.startswith('/vendor/vouchers') or path.endswith('/promotions'):
            (self.seed.out/('form-'+str(len(self.seed.http))+'.private.html')).write_bytes(body)
        return body
    def json(self,path,values,extra=None):
        headers={'Content-Type':'application/json','Accept':'application/json','X-XSRF-TOKEN':self.token()}; headers.update(extra or {})
        body=self.call(path,json.dumps(values).encode(),headers)
        return json.loads(body) if body else None
    def upload(self,path,fields,field='image'):
        boundary='qa03'+uuid.uuid4().hex; values=dict(fields,_csrf=self.token()); body=bytearray()
        for key,value in values.items(): body.extend(('--'+boundary+'\r\nContent-Disposition: form-data; name="'+key+'"\r\n\r\n'+str(value)+'\r\n').encode())
        body.extend(('--'+boundary+'\r\nContent-Disposition: form-data; name="'+field+'"; filename="fixture.png"\r\nContent-Type: image/png\r\n\r\n').encode()); body.extend(image()); body.extend(('\r\n--'+boundary+'--\r\n').encode())
        return self.call(path,bytes(body),{'Content-Type':'multipart/form-data; boundary='+boundary})
    def login(self,name):
        self.form('/login',{'identifier':name,'password':self.seed.password}); self.call('/user/profile')

class Seed:
    def __init__(self,root,out,password,include_refunds=False):
        self.include_refunds=include_refunds; self.refund_fixtures_loaded=False
        self.db='qa02_refund_seed' if include_refunds else 'postgres'
        self.root=root; self.out=out; self.password=password; self.name='qa03-seed-'+uuid.uuid4().hex[:10]
        self.env=os.environ.copy(); self.flags=getattr(subprocess,'CREATE_NO_WINDOW',0)
        self.created=False; self.app=None; self.log=None; self.http=[]; self.state={'actors':{},'products':[],'orders':{},'complete':False}
        self.source=self.git('rev-parse','HEAD'); self.result={'source_sha':self.source,'demo_opt_in':True,'application_profiles':['local','demo'],'scope':'Full integrated fictional demo dataset on disposable local DB; not browser, clean-host or production acceptance','steps':{},'http_trace':self.http,'bootstrap_exception':'Only initial ADMIN role membership is bootstrapped on the newly registered, OTP-verified fictional account in the owned empty QA DB. All other business writes use real HTTP/service boundaries.'}
        if include_refunds:
            self.result['bootstrap_exception']='Shared fixtures use real HTTP with initial ADMIN role bootstrap. The TD-owned test-classpath module additionally sets up fictional identity/catalog fixtures and synthetic persisted ONLINE PAID prerequisites in this same owned disposable DB. This is not gateway verification or a production PAID writer.'
            self.result['combined_rerun_scope']='Shared module: same-process completed checkpoint validation. TD refund module: two independent application invocations. Both must preserve the full combined database fingerprint.'
    def git(self,*args): return subprocess.check_output(['git',*args],cwd=self.root,text=True).strip()
    def command(self,args,data=None,env=None,timeout=60):
        p=subprocess.run(args,input=data,stdout=subprocess.PIPE,stderr=subprocess.PIPE,env=env or self.env,timeout=timeout,creationflags=self.flags)
        if p.returncode:
            (self.out/'last-command-error.private.log').write_bytes(p.stderr); raise SeedError('OWNED_COMMAND_FAILED')
        return p.stdout
    def sql(self,query):
        require(query.lstrip().upper().startswith(('SELECT','SHOW')),'READ_ONLY_SQL_REQUIRED')
        return self.command(['docker','exec',self.name,'psql','-X','-A','-t','-v','ON_ERROR_STOP=1','-U','postgres','-d',self.db,'-c',query]).decode().strip()
    def one(self,query):
        value=self.sql(query); require(bool(value),'FIXTURE_NOT_FOUND'); return int(value)
    def version(self,table,key):
        require(table in ('users','shops','products','categories','orders','shipments','return_requests'),'INVALID_TABLE')
        return self.one('SELECT version FROM uteexpress.'+table+' WHERE id='+str(int(key)))
    def oid(self,key): return self.state['orders'][key]
    def start(self):
        require(not self.git('status','--porcelain'),'CHECKOUT_NOT_CLEAN')
        self.jar=self.root/'target/uteexpress-0.1.0-SNAPSHOT.jar'; require(self.jar.is_file(),'PACKAGE_SOURCE_FIRST')
        self.result['jar_sha256']=hashlib.sha256(self.jar.read_bytes()).hexdigest()
        with zipfile.ZipFile(self.jar) as jar:
            prefix='BOOT-INF/classes/com/uteexpress/'
            stale=[n for n in jar.namelist() if n.startswith(prefix) and n.endswith('.class') and not (self.root/'src/main/java/com/uteexpress'/(n[len(prefix):].split('$',1)[0].removesuffix('.class')+'.java')).is_file()]
        require(not stale,'STALE_CLASSES_RUN_CLEAN_PACKAGE')
        mail=socketserver.ThreadingTCPServer(('127.0.0.1',0),MailHandler); mail.daemon_threads=True; mail.codes={}; mail.mail_lock=threading.Lock(); self.mail=mail
        threading.Thread(target=mail.serve_forever,daemon=True).start()
        env=self.env.copy(); env['POSTGRES_PASSWORD']=secrets.token_urlsafe(32)
        self.command(['docker','run','-d','--name',self.name,'--label','uteexpress.qa03=disposable','-e','POSTGRES_PASSWORD','-e','POSTGRES_DB='+self.db,'-p','127.0.0.1::5432','postgres:17.6'],env=env);self.created=True
        for _ in range(45):
            p=subprocess.run(['docker','exec',self.name,'pg_isready','-U','postgres'],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,creationflags=self.flags)
            if p.returncode==0:break
            time.sleep(1)
        else:raise SeedError('DATABASE_START_TIMEOUT')
        port=int(self.command(['docker','port',self.name,'5432/tcp']).decode().strip().rsplit(':',1)[1])
        with socket.socket() as sock:sock.bind(('127.0.0.1',0)); webport=sock.getsockname()[1]
        self.base='http://127.0.0.1:'+str(webport)
        db_password=env['POSTGRES_PASSWORD']
        os_keys={'SYSTEMROOT','WINDIR','COMSPEC','PATH','PATHEXT','TEMP','TMP','USERPROFILE','HOMEDRIVE','HOMEPATH','LOCALAPPDATA','APPDATA','PROGRAMFILES','PROGRAMW6432','COMMONPROGRAMFILES','JAVA_HOME'}
        env={key:value for key,value in self.env.items() if key.upper() in os_keys}
        self.result['isolated_app_environment']=True
        self.result['ignored_parent_override_names']=[key for key in ('SPRING_DATASOURCE_URL','SPRING_CONFIG_LOCATION','SPRING_APPLICATION_JSON') if key in self.env]
        env.update(DB_URL='jdbc:postgresql://127.0.0.1:'+str(port)+'/'+self.db,DB_USERNAME='postgres',DB_PASSWORD=db_password,JWT_SECRET_BASE64=base64.b64encode(secrets.token_bytes(32)).decode(),OTP_PEPPER_BASE64=base64.b64encode(secrets.token_bytes(32)).decode(),JWT_COOKIE_SECURE='false',MAIL_HOST='127.0.0.1',MAIL_PORT=str(mail.server_address[1]),MAIL_FROM='seed@example.test',MAIL_SMTP_AUTH='false',MAIL_STARTTLS='false',UPLOAD_DIR=str(self.out/'uploads'))
        java=pathlib.Path(os.environ.get('JAVA_HOME',''))/'bin/java.exe'; require(java.is_file(),'JDK21_REQUIRED')
        self.app_env=env.copy(); self.java=java
        self.log=(self.out/'app.private.log').open('wb')
        self.app=subprocess.Popen([str(java),'-Duser.timezone=UTC','-jar',str(self.jar),'--spring.profiles.active=local,demo','--spring.config.location=classpath:/application.yml','--spring.datasource.url='+env['DB_URL'],'--spring.datasource.username=postgres','--server.address=127.0.0.1','--server.port='+str(webport),'--management.health.mail.enabled=false'],cwd=self.root,env=env,stdout=self.log,stderr=subprocess.STDOUT,creationflags=self.flags)
        for _ in range(120):
            require(self.app.poll() is None,'APPLICATION_START_FAILED')
            try:
                with urllib.request.urlopen(self.base+'/actuator/health',timeout=2) as response:
                    if response.status==200 and b'UP' in response.read():break
            except (OSError,urllib.error.HTTPError):pass
            time.sleep(1)
        else:raise SeedError('APPLICATION_START_TIMEOUT')
        self.result['steps']['clean_migration']={'status':'PASS','successful_migrations':self.one('SELECT count(*) FROM uteexpress.flyway_schema_history WHERE success'),'role_codes':self.sql("SELECT string_agg(code,',' ORDER BY code) FROM uteexpress.roles")}
    def register(self,key):
        username='qa03seed_'+key; address=username+'@example.test'; client=Client(self)
        client.form('/register',dict(username=username,email=address,password=self.password,confirmPassword=self.password))
        with self.mail.mail_lock:code=self.mail.codes.get(address)
        require(bool(code),'SMTP_OTP_NOT_CAPTURED')
        client.form('/verify-otp',dict(identifier=username,code=code))
        uid=self.one("SELECT id FROM uteexpress.users WHERE normalized_username='"+username+"' AND status='ACTIVE' AND password_hash LIKE '$2%' AND email_verified_at IS NOT NULL")
        self.state['actors'][key]=dict(id=uid,username=username,email=address);client.login(username);return client
    def actor(self,key):
        client=Client(self);client.login(self.state['actors'][key]['username']);return client
    def fixtures(self):
        keys=('admin','manager','shipper_a','shipper_b','buyer_a','buyer_b','vendor_a','vendor_b','pending','rejected')
        clients={k:self.register(k) for k in keys};admin_id=self.state['actors']['admin']['id']
        require(self.one("SELECT count(*) FROM uteexpress.user_roles ur JOIN uteexpress.roles r ON r.id=ur.role_id WHERE r.code='ADMIN'")==0,'NOT_AN_EMPTY_OWNED_ADMIN_FIXTURE')
        self.command(['docker','exec',self.name,'psql','-X','-v','ON_ERROR_STOP=1','-U','postgres','-d',self.db,'-c',"INSERT INTO uteexpress.user_roles(user_id,role_id) SELECT "+str(admin_id)+",id FROM uteexpress.roles WHERE code='ADMIN'"])
        admin=self.actor('admin');self.admin=admin
        for key,role in (('manager','MANAGER'),('shipper_a','SHIPPER'),('shipper_b','SHIPPER')):
            uid=self.state['actors'][key]['id'];admin.form('/admin/accounts/'+str(uid)+'/roles/'+role+'/grant',{'version':self.version('users',uid)})
        for key in ('vendor_a','vendor_b','pending','rejected'):
            clients[key].form('/user/shop/register',{'name':'QA03 '+key,'slug':'qa03-'+key.replace('_','-'),'pickupAddress':'Fictional QA pickup, HCM','description':'Synthetic integrated seed'})
            shop=self.one('SELECT id FROM uteexpress.shops WHERE owner_id='+str(self.state['actors'][key]['id']));self.state[key+'_shop']=shop
            if key!='pending':admin.form('/admin/shops/'+str(shop)+('/reject' if key=='rejected' else '/approve'),{'version':self.version('shops',shop),'reason':'Fictional demo rejection'})
        for key in ('buyer_a','buyer_b'):
            clients[key].form('/user/addresses',{'receiverName':'Fictional '+key,'phone':'0900000000','provinceCode':'HCM','district':'Q1','detail':'Synthetic QA delivery address'})
            self.state[key+'_address']=self.one('SELECT id FROM uteexpress.addresses WHERE user_id='+str(self.state['actors'][key]['id']))
        self.vendor_a=self.actor('vendor_a');self.vendor_b=self.actor('vendor_b');self.shipper=self.actor('shipper_a');self.buyer=clients['buyer_a'];self.buyer_b=clients['buyer_b']
        for key in ('a','b','inactive'):
            admin.form('/admin/categories',{'name':'QA03 '+key,'slug':'qa03-'+key,'description':'Synthetic'})
            cid=self.one("SELECT id FROM uteexpress.categories WHERE slug='qa03-"+key+"'");self.state['category_'+key]=cid
            if key=='inactive':admin.form('/admin/categories/'+str(cid)+'/disable',{'version':self.version('categories',cid)})
        for code,active in (('QA03_SEED',True),('QA03_INACTIVE',False)):
            admin.form('/admin/shipping/providers',{'code':code,'name':code,'active':str(active).lower()})
        self.state['provider']=self.one("SELECT id FROM uteexpress.shipping_providers WHERE code='QA03_SEED'")
        admin.form('/admin/shipping/rates',{'providerId':self.state['provider'],'serviceCode':'STANDARD','destinationRegion':'HCM','fee':15000,'active':'true'})
        admin.form('/admin/commissions',{'ratePercent':7,'effectiveFrom':'2000-01-01T00:00:00Z'})
        for i in range(14):
            owner=self.vendor_a if i<10 else self.vendor_b;shop=self.state['vendor_a_shop' if i<10 else 'vendor_b_shop'];name='QA03 Product '+str(i)
            owner.form('/vendor/products',{'name':name,'description':'Fictional integrated demo item','price':100000 if i<10 else 50000,'stock':0 if i==11 else 50,'categoryId':self.state['category_a' if i<10 else 'category_b']})
            pid=self.one("SELECT id FROM uteexpress.products WHERE shop_id="+str(shop)+" AND name='"+name+"'");self.state['products'].append(pid)
            owner.upload('/vendor/products/'+str(pid)+'/images',{'altText':'Fictional QA image'})
            if i==12:owner.form('/vendor/products/'+str(pid)+'/delete',{'version':self.version('products',pid)})
            if i==13:admin.form('/admin/products/'+str(pid)+'/hide',{'version':self.version('products',pid),'reason':'Synthetic moderation case'})
        now=dt.datetime.now(dt.timezone(dt.timedelta(hours=7)));window={'startsAt':(now-dt.timedelta(days=1)).strftime('%Y-%m-%dT%H:%M'),'endsAt':(now+dt.timedelta(days=30)).strftime('%Y-%m-%dT%H:%M'),'active':'true'}
        for vendor,code,value in ((self.vendor_a,'QA03_VOUCHER_A',10000),(self.vendor_b,'QA03_VOUCHER_B',5000)):
            vendor.form('/vendor/vouchers',dict(window,code=code,type='FIXED',value=value,minSubtotal=0,totalLimit=100,perUserLimit=30))
        self.vendor_a.form('/vendor/products/'+str(self.state['products'][0])+'/promotions',dict(window,name='QA03 Promotion',discountPercent=10))
        require(self.one('SELECT count(*) FROM uteexpress.product_images')==14,'PRODUCT_IMAGES_MISSING')
        for key,expected in (('vendor_a','APPROVED'),('vendor_b','APPROVED'),('pending','PENDING'),('rejected','REJECTED')):
            require(self.sql('SELECT status FROM uteexpress.shops WHERE id='+str(self.state[key+'_shop']))==expected,'SHOP_STATE_'+key)
        guest=Client(self)
        for index,pid in enumerate(self.state['products']):
            guest.call('/products/'+str(pid),allowed=(200,404))
            require(self.http[-1]['status']==(200 if index<12 else 404),'PUBLIC_PRODUCT_VISIBILITY')
        require(self.one('SELECT count(*) FROM uteexpress.vouchers')==2,'FOUNDATION_VOUCHER_MISSING')
        require(self.one('SELECT count(*) FROM uteexpress.promotions')==1,'FOUNDATION_PROMOTION_MISSING')
        self.result['steps']['foundation']={'status':'PASS','registered_and_OTP_verified_accounts':10,'shops':{'approved':2,'pending':1,'rejected':1},'products':14,'public_products':12,'nonpublic_products':2,'images':14,'password_hashing':'Real registration/identity PasswordEncoder; password from process ENV'}
    def fulfillment(self,order,**extra):
        ov=self.version('orders',order);sv=self.one('SELECT version FROM uteexpress.shipments WHERE order_id='+str(order));return dict(expectedOrderVersion=ov,expectedShipmentVersion=sv,**extra)
    def return_versions(self,order):return dict(expectedVersion=self.version('orders',order),expectedRequestVersion=self.one('SELECT version FROM uteexpress.return_requests WHERE order_id='+str(order)))
    def orders(self):
        for scenario in ('NEW','ASSIGNED','SHIPPING','DELIVERED','REFUNDED','FAILED','RTS','BUYER_CANCEL','OTHER_SHOP_NEW'):
            other=scenario=='OTHER_SHOP_NEW';buyer=self.buyer_b if other else self.buyer;vendor=self.vendor_b if other else self.vendor_a
            product=self.state['products'][10 if other else 0];quantity=1 if other else 2
            buyer.json('/user/cart/items',{'productId':product,'quantity':quantity})
            key='qa03-seed-'+scenario.lower();selection=dict(addressId=self.state['buyer_b_address' if other else 'buyer_a_address'],shippingProviderId=self.state['provider'],shippingServiceCode='STANDARD',voucherCode='QA03_VOUCHER_B' if other else 'QA03_VOUCHER_A')
            quote=buyer.json('/user/checkout/quote',selection);require(quote['quote']['totals']['grandTotal']==(60000 if other else 185000),'QUOTE_SNAPSHOT_MISMATCH')
            payload=dict(selection,checkoutKey=key,items=[dict(productId=product,quantity=quantity)],paymentMethod='COD')
            created=buyer.json('/user/checkout/place-order',payload);order=int(created['orderId']);self.state['orders'][scenario]=order
            replay=buyer.json('/user/checkout/place-order',payload);require(replay['replayed'] and int(replay['orderId'])==order,'CHECKOUT_REPLAY_MISMATCH')
            if scenario in ('NEW','OTHER_SHOP_NEW'):continue
            if scenario=='BUYER_CANCEL':buyer.json('/buyer/orders/'+str(order)+'/cancel',{'expectedOrderVersion':self.version('orders',order)});continue
            vendor.json('/vendor/orders/'+str(order)+'/confirm',{'expectedVersion':self.version('orders',order)})
            vendor.json('/vendor/orders/'+str(order)+'/ready',{'expectedVersion':self.version('orders',order)})
            self.admin.json('/admin/orders/'+str(order)+'/assign',{'shipperId':self.state['actors']['shipper_a']['id'],'expectedOrderVersion':self.version('orders',order)})
            if scenario=='ASSIGNED':continue
            for operation in ('pickup','shipping'):self.shipper.json('/shipper/orders/'+str(order)+'/'+operation,self.fulfillment(order))
            if scenario=='SHIPPING':continue
            if scenario in ('FAILED','RTS'):
                original=self.version('orders',order)
                self.shipper.json('/shipper/orders/'+str(order)+'/delivery-failed',self.fulfillment(order,failureReason='Fictional recipient unavailable',evidenceReference='QA03-ATTEMPT-1'))
                self.shipper.json('/shipper/orders/'+str(order)+'/retry',self.fulfillment(order))
                self.shipper.json('/shipper/orders/'+str(order)+'/delivery-failed',self.fulfillment(order,failureReason='Fictional second failure',evidenceReference='QA03-ATTEMPT-2'))
                require(self.version('orders',order)==original,'FAILURE_RETRY_CHANGED_ORDER_VERSION')
                if scenario=='FAILED':continue
                receipt=self.fulfillment(order,receiptReference='QA03-FICTIONAL-RTS-RECEIPT');self.admin.json('/admin/orders/'+str(order)+'/return-to-sender',receipt)
                require(self.admin.json('/admin/orders/'+str(order)+'/return-to-sender',receipt)['replayed'],'RTS_REPLAY_MISMATCH');continue
            self.shipper.json('/shipper/orders/'+str(order)+'/delivered',self.fulfillment(order,collectedAmount=185000))
            item=self.one('SELECT id FROM uteexpress.order_items WHERE order_id='+str(order))
            buyer.upload('/order-items/'+str(item)+'/review',{'rating':5,'comment':'This fictional demo order was delivered correctly and the review contains sufficient plain text for validation.'},field='images')
            if scenario=='REFUNDED':
                buyer.json('/orders/'+str(order)+'/return',{'expectedVersion':self.version('orders',order),'reason':'Fictional full return'})
                vendor.json('/vendor/orders/'+str(order)+'/return/approve',self.return_versions(order))
                vendor.json('/vendor/orders/'+str(order)+'/return/receive',dict(self.return_versions(order),receiptReference='QA03-FICTIONAL-BUYER-RECEIPT'))
                request=dict(expectedOrderVersion=self.version('orders',order),expectedReturnRequestVersion=self.one('SELECT version FROM uteexpress.return_requests WHERE order_id='+str(order)),proofReference='QA03-FICTIONAL-NOT-REAL-MONEY');headers={'Idempotency-Key':'qa03-refund-one'}
                self.admin.json('/admin/orders/'+str(order)+'/refunds',request,headers)
                require(self.admin.json('/admin/orders/'+str(order)+'/refunds',request,headers)['replay'],'REFUND_REPLAY_MISMATCH')
        for client,pid in ((self.buyer,self.state['products'][0]),(self.buyer_b,self.state['products'][10])):
            client.form('/user/favorites/'+str(pid),{});client.call('/products/'+str(pid))
        for selected,pid in ((True,self.state['products'][1]),(False,self.state['products'][10])):
            self.buyer.json('/user/cart/items',{'productId':pid,'quantity':1})
            iid=self.one('SELECT ci.id FROM uteexpress.cart_items ci JOIN uteexpress.carts c ON c.id=ci.cart_id WHERE c.user_id='+str(self.state['actors']['buyer_a']['id'])+' AND ci.product_id='+str(pid))
            self.buyer.json('/user/cart/items/'+str(iid)+'/selection',{'selected':selected})
    def fingerprint(self):
        rows=[];counts={}
        for table in self.sql("SELECT tablename FROM pg_tables WHERE schemaname='uteexpress' ORDER BY tablename").splitlines():
            require(re.fullmatch('[a-z_][a-z_0-9]*',table),'INVALID_SCHEMA_TABLE')
            row=self.sql('SELECT count(*),md5(coalesce(string_agg(md5(row_to_json(t)::text),\'\' ORDER BY md5(row_to_json(t)::text)),\'\')) FROM uteexpress."'+table+'" t');count,digest=row.split('|');counts[table]=int(count);rows.append(table+'|'+row)
        return {'counts':counts,'row_fingerprint_sha256':hashlib.sha256('\n'.join(rows).encode()).hexdigest()}
    def validate(self):
        require(self.one('SELECT count(DISTINCT selected) FROM uteexpress.cart_items')==2,'CART_SELECTION_FIXTURES_MISSING')
        require(self.sql("SELECT string_agg(code,',' ORDER BY code) FROM uteexpress.roles")=='ADMIN,MANAGER,SHIPPER,USER,VENDOR','ROLE_MANIFEST_MISMATCH')
        require(len(self.state['orders'])==9 and len(self.state['products'])==14,'SEED_STATE_INCOMPLETE')
        expected={'NEW':'NEW','ASSIGNED':'CONFIRMED','SHIPPING':'SHIPPING','DELIVERED':'DELIVERED','REFUNDED':'REFUNDED','FAILED':'SHIPPING','RTS':'CANCELLED','BUYER_CANCEL':'CANCELLED','OTHER_SHOP_NEW':'NEW'}
        for key,status in expected.items():
            oid=self.oid(key);actual=self.sql('SELECT status FROM uteexpress.orders WHERE id='+str(oid));require(actual==status,'ORDER_STATUS_'+key)
            require(self.one('SELECT count(*) FROM uteexpress.order_status_history WHERE order_id='+str(oid))>0,'ORDER_HISTORY_MISSING')
        queries={
            'all_constraints_validated':"SELECT count(*) FROM pg_constraint c JOIN pg_namespace n ON n.oid=c.connamespace WHERE n.nspname='uteexpress' AND NOT c.convalidated",
            'payment_amount_matches_order':'SELECT count(*) FROM uteexpress.payments p JOIN uteexpress.orders o ON o.id=p.order_id WHERE p.amount<>o.grand_total',
            'refund_bound_to_paid_order':"""SELECT count(*) FROM uteexpress.refunds r JOIN uteexpress.payments p ON p.id=r.payment_id JOIN uteexpress.orders o ON o.id=r.order_id
                LEFT JOIN uteexpress.return_requests t ON t.id=r.return_request_id
                WHERE p.order_id<>o.id OR p.status<>'PAID' OR r.amount<>p.amount OR r.amount<>o.grand_total
                OR NOT CASE WHEN r.operation='PAY02_MANUAL_COD_REFUND_V1' THEN
                    o.status='REFUNDED' AND t.id IS NOT NULL AND t.order_id=o.id AND t.status='APPROVED'
                    AND r.return_received_at=t.received_at AND r.order_cancelled_at IS NULL
                    AND r.result_order_version=r.expected_order_version+1
                WHEN r.operation='PAY02_CANCELLED_PAID_REFUND_V1' THEN
                    o.status='CANCELLED' AND r.return_request_id IS NULL AND r.return_received_at IS NULL
                    AND r.expected_return_request_version IS NULL AND r.order_cancelled_at=o.cancelled_at
                    AND r.payment_paid_at<=r.order_cancelled_at AND r.order_cancelled_at<=r.server_timestamp
                    AND r.result_order_version=r.expected_order_version
                ELSE false END IS TRUE""",
            'one_default_address':'SELECT count(*) FROM (SELECT user_id FROM uteexpress.addresses WHERE is_default GROUP BY user_id HAVING count(*)>1) s',
            'nonnegative_stock':'SELECT count(*) FROM uteexpress.products WHERE stock<0',
            'history_matches_current_order':"SELECT count(*) FROM uteexpress.orders o JOIN (SELECT DISTINCT ON(order_id) order_id,to_status FROM uteexpress.order_status_history ORDER BY order_id,id DESC) h ON h.order_id=o.id WHERE o.status<>h.to_status",
            'item_totals_match_snapshot':'SELECT count(*) FROM uteexpress.orders o JOIN (SELECT order_id,sum(line_total) total FROM uteexpress.order_items GROUP BY order_id) i ON i.order_id=o.id WHERE o.subtotal<>i.total',
            'voucher_total_quota':"SELECT count(*) FROM (SELECT v.id FROM uteexpress.vouchers v JOIN uteexpress.voucher_usages u ON u.voucher_id=v.id WHERE u.status<>'RELEASED' GROUP BY v.id,v.total_limit HAVING count(*)>v.total_limit) q",
            'voucher_user_quota':"SELECT count(*) FROM (SELECT v.id,u.user_id FROM uteexpress.vouchers v JOIN uteexpress.voucher_usages u ON u.voucher_id=v.id WHERE u.status<>'RELEASED' GROUP BY v.id,u.user_id,v.per_user_limit HAVING count(*)>v.per_user_limit) q"}
        for code,query in queries.items():require(self.one(query)==0,'INVARIANT_'+code)
        require(self.one('SELECT stock FROM uteexpress.products WHERE id='+str(self.state['products'][0]))==38,'STOCK_RESERVATION_RELEASE_MISMATCH')
        require(self.one('SELECT count(*) FROM uteexpress.reviews')==2,'REVIEW_COUNT_MISMATCH')
        require(self.one('SELECT count(*) FROM uteexpress.review_media')==2,'REVIEW_MEDIA_MISMATCH')
        require(self.one('SELECT count(*) FROM uteexpress.refunds')==(3 if self.refund_fixtures_loaded else 1),'REFUND_COUNT_MISMATCH')
        require(self.one('SELECT count(*) FROM uteexpress.shipment_return_receipts')==1,'RTS_RECEIPT_COUNT_MISMATCH')
        require(self.one('SELECT count(*) FROM uteexpress.notifications')>0,'DURABLE_INBOX_MISSING')
        day=self.sql("SELECT (min(created_at) AT TIME ZONE 'Asia/Ho_Chi_Minh')::date FROM uteexpress.orders")
        end=(dt.date.fromisoformat(day)+dt.timedelta(days=2)).isoformat();params=urllib.parse.urlencode({'from':day,'toExclusive':end})
        money={'deliveredSales':340000,'collectedPayments':340000,'refunds':170000,'netCollectedRevenue':170000,'netCommission':11900,'netRevenueAfterCommission':158100}
        for actor,path in ((self.vendor_a,'/vendor/dashboard'),(self.admin,'/admin/dashboard/revenue/metrics')):
            scope_params=params
            if self.refund_fixtures_loaded and actor is self.admin:scope_params+='&shopId='+str(self.state['vendor_a_shop'])
            report=json.loads(actor.call(path+'?'+scope_params,headers={'Accept':'application/json'}))['selected']['metrics']
            for metric,value in money.items():require(report[metric]['complete'] and report[metric]['unknownEvents']==0 and report[metric]['value']==value,'FINANCIAL_METRIC_'+metric)
        return {'status':'PASS','order_states':expected,'constraint_and_business_checks':list(queries),'financial_expected_and_actual':money,'financial_scope':'Vendor A and Admin Shop A filter when combined (otherwise Admin global), same-period synthetic dataset, shipping excluded','role_passwords_reset':False}
    def refund_process_diagnostics(self,number,log,exit_code,timed_out=False):
        # Explicit allowlist: never publish provider/proof values, credentials, cookies or log bodies.
        diagnostic={'invocation':number,'application_exit_code':exit_code,'timed_out':timed_out,
                    'captured_after_process_exit':True,'log_sha256':hashlib.sha256(log.read_bytes()).hexdigest()}
        try:
            diagnostic['persisted_facts']=json.loads(self.sql("""
                SELECT coalesce(json_agg(row_to_json(f) ORDER BY f.order_id),'[]'::json) FROM (
                  SELECT o.id AS order_id,p.id AS payment_id,o.status AS order_status,o.version AS order_version,
                    o.created_at AS order_created_at,o.cancelled_at,o.inventory_released_at,o.grand_total,
                    p.method,p.status AS payment_status,p.amount,p.created_at AS payment_created_at,
                    p.paid_at,p.expired_at,
                    p.amount=o.grand_total AS amount_matches,
                    p.paid_at>=p.created_at AS paid_not_before_creation,
                    p.paid_at<=o.cancelled_at AS paid_not_after_cancellation,
                    p.expired_at IS NULL AS not_expired,
                    (p.provider_reference IS NOT NULL AND length(trim(p.provider_reference))>0) AS provider_reference_present,
                    (SELECT count(*) FROM uteexpress.payments q WHERE q.order_id=o.id AND q.status='PAID') AS paid_count,
                    EXISTS(SELECT 1 FROM uteexpress.refunds r WHERE r.order_id=o.id) AS refund_exists
                  FROM uteexpress.orders o JOIN uteexpress.payments p ON p.order_id=o.id
                  JOIN uteexpress.users u ON u.id=o.buyer_id
                  WHERE u.normalized_username='qa02_refund_buyer' ORDER BY o.id,p.id LIMIT 100
                ) f
                """))
            diagnostic['capture_status']='CAPTURED'
        except Exception as error:
            diagnostic.update(capture_status='UNAVAILABLE',capture_error_type=type(error).__name__)
        return diagnostic
    def refund_fixture_process(self,number):
        # TD-owned test-classpath fixture; synthetic PAID setup is explicit and never a production writer.
        source=self.root/'target/test-classes/qa02/seed'
        require(source.is_dir(),'BUILD_QA02_REFUND_FIXTURE_FIRST')
        classes=self.out/'qa02-test-classes'
        if not classes.exists():shutil.copytree(source,classes/'qa02/seed')
        env=self.app_env.copy();env.update(QA02_DISPOSABLE_SEED_CONFIRM='YES',QA02_REFUND_DEMO_PASSWORD=self.password)
        args=[str(self.java),'-Duser.timezone=UTC','-Dloader.path='+str(classes),'-Dloader.main=qa02.seed.RefundAcceptanceSeed','-cp',str(self.jar),'org.springframework.boot.loader.launch.PropertiesLauncher','--spring.profiles.active=local,demo','--spring.config.location=classpath:/application.yml','--server.address=127.0.0.1','--server.port=0','--management.health.mail.enabled=false','--uteexpress.demo.shipping.enabled=false','--uteexpress.demo.catalog.enabled=false']
        log=self.out/('qa02-refund-'+str(number)+'.private.log')
        try:
            with log.open('wb') as output:
                process=subprocess.run(args,cwd=self.root,env=env,stdout=output,stderr=subprocess.STDOUT,timeout=180,creationflags=self.flags)
        except subprocess.TimeoutExpired:
            self.result.setdefault('refund_fixture_failures',[]).append(self.refund_process_diagnostics(number,log,None,True))
            raise
        diagnostics=self.refund_process_diagnostics(number,log,process.returncode)
        if process.returncode!=0:self.result.setdefault('refund_fixture_failures',[]).append(diagnostics)
        require(process.returncode==0,'QA02_FIXTURE_PROCESS_FAILED_'+str(number))
        lines=log.read_text(encoding='utf-8',errors='replace').splitlines()
        result=json.loads(next(line.split('=',1)[1] for line in lines if line.startswith('QA02_REFUND_SEED_RESULT=')))
        require(all(value=='PASS' for value in result['negativeChecks'].values()),'QA02_NEGATIVE_CHECK_NOT_PASS')
        return {'status':'PASS','application_exit_code':process.returncode,'log_sha256':hashlib.sha256(log.read_bytes()).hexdigest(),'result':result,'persisted_diagnostics':diagnostics}
    def existing_returned_refund_digest(self):
        value=self.sql('SELECT row_to_json(r)::text FROM uteexpress.refunds r WHERE r.order_id='+str(self.oid('REFUNDED')))
        require(bool(value),'ORIGINAL_RETURNED_REFUND_MISSING');return hashlib.sha256(value.encode()).hexdigest()
    def validate_combined_finance(self):
        day=self.sql("SELECT (min(created_at) AT TIME ZONE 'Asia/Ho_Chi_Minh')::date FROM uteexpress.orders")
        end=(dt.date.fromisoformat(day)+dt.timedelta(days=2)).isoformat()
        report=json.loads(self.admin.call('/admin/dashboard/revenue/metrics?'+urllib.parse.urlencode({'from':day,'toExclusive':end}),headers={'Accept':'application/json'}))['selected']['metrics']
        expected={'deliveredSales':720000,'refunds':550000,'netCommission':25200}
        for metric,value in expected.items():require(report[metric]['complete'] and report[metric]['value']==value,'COMBINED_FINANCE_'+metric)
        # TD deliberately includes a PAID NEW ONLINE negative fixture. Global collection must fail closed.
        for metric in ['collectedPayments','netCollectedRevenue','netRevenueAfterCommission']:
            require(not report[metric]['complete'] and report[metric]['value'] is None and report[metric]['unknownEvents']==1,'COMBINED_AMBIGUITY_'+metric)
        return {'status':'PASS','complete_metrics':expected,'unavailable_metrics':['collectedPayments','netCollectedRevenue','netRevenueAfterCommission'],'reason':'One deliberately invalid-for-revenue PAID NEW ONLINE fixture; unknownEvents=1, value=null, not silently excluded'}
    def seed_once(self):
        if self.state['complete']:return self.validate()
        self.fixtures();self.orders();result=self.validate();self.state['complete']=True
        (self.out/'seed-state.private.json').write_text(json.dumps(self.state,indent=2),encoding='utf-8')
        return result
    def run(self):
        try:
            self.start();self.result['steps']['first_seed']=self.seed_once()
            if self.include_refunds:
                old_digest=self.existing_returned_refund_digest()
                self.result['steps']['qa02_first_process']=self.refund_fixture_process(1)
                self.refund_fixtures_loaded=True
                self.result['steps']['combined_first_validation']=self.validate()
                self.result['steps']['combined_finance']=self.validate_combined_finance()
                require(self.existing_returned_refund_digest()==old_digest,'OLD_RETURNED_LEDGER_CHANGED')
                self.result['old_returned_refund_preserved_sha256']=old_digest
            # Deliberate legitimate edit proves the rerun does not reset mutable product data.
            pid=self.state['products'][0]
            self.vendor_a.form('/vendor/products/'+str(pid)+'/update',dict(name='QA03 Preserved live edit',description='Edited after seeding',price=100000,stock=38,categoryId=self.state['category_a'],version=self.version('products',pid)))
            require(self.sql('SELECT name FROM uteexpress.products WHERE id='+str(pid))=='QA03 Preserved live edit','LIVE_EDIT_NOT_APPLIED')
            before=self.fingerprint();writes=sum(e['method']=='POST' for e in self.http)
            if self.include_refunds:self.result['steps']['qa02_second_process']=self.refund_fixture_process(2)
            self.result['steps']['second_seed']=self.seed_once()
            if self.include_refunds:self.result['steps']['combined_finance_second']=self.validate_combined_finance()
            after=self.fingerprint()
            require(before==after,'RERUN_CHANGED_BUSINESS_DATA');require(sum(e['method']=='POST' for e in self.http)==writes,'RERUN_REPLAYED_HTTP_MUTATIONS')
            self.result['steps']['idempotency']={'status':'PASS','complete_business_fingerprint_unchanged':True,'preserved_live_product_edit':True,'second_seed_http_writes':0,'before':before,'after':after,'policy':'Manifest-backed validate-and-preserve rerun; incomplete/missing fixtures fail closed, never reset statuses/passwords/stock'}
            self.result['fixture_ids']={'orders':self.state['orders'],'products':self.state['products'],'actors':{k:v['id'] for k,v in self.state['actors'].items()}}
            self.result['dump_sha256']=hashlib.sha256(self.command(['docker','exec',self.name,'pg_dump','-U','postgres','-d',self.db,'-Fc'])).hexdigest()
            require(self.git('rev-parse','HEAD')==self.source and not self.git('status','--porcelain'),'SOURCE_CHANGED_DURING_REHEARSAL');self.result['status']='PASS'
        except Exception as error:
            self.result.update(status='FAIL',failure_code=str(error) if isinstance(error,SeedError) else type(error).__name__)
        finally:
            if self.app and self.app.poll() is None:
                self.app.terminate()
                try:self.app.wait(timeout=20)
                except subprocess.TimeoutExpired:self.app.kill();self.app.wait(timeout=10)
            if self.log:self.log.close()
            if hasattr(self,'mail'):self.mail.shutdown();self.mail.server_close()
            if self.created:self.result['owned_container_removed']=subprocess.run(['docker','rm','-f',self.name],stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL,creationflags=self.flags).returncode==0
            self.result['script_sha256']=hashlib.sha256(pathlib.Path(__file__).read_bytes()).hexdigest()
            if (self.out/'app.private.log').exists():self.result['app_log_sha256']=hashlib.sha256((self.out/'app.private.log').read_bytes()).hexdigest()
            (self.out/'result.json').write_text(json.dumps(self.result,indent=2)+'\n',encoding='utf-8')
            print(json.dumps({k:v for k,v in self.result.items() if k!='http_trace'},indent=2));print('LOCAL_EVIDENCE='+str(self.out/'result.json'))
        return 0 if self.result['status']=='PASS' else 1

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--checkout',type=pathlib.Path,default=pathlib.Path.cwd());parser.add_argument('--include-qa02-refunds',action='store_true',help='Run TD test-classpath synthetic PAID fixture twice in the same owned DB');parser.add_argument('--disposable-demo',action='store_true',required=True,help='Explicitly opt in to fictional demo on a newly owned disposable DB');args=parser.parse_args();root=args.checkout.resolve()
    password=os.environ.get('QA03_DEMO_PASSWORD','');require(len(password)>=(16 if args.include_qa02_refunds else 12),'QA03_DEMO_PASSWORD_REQUIRED')
    out=root/'.work/qa03-integrated-seed'/('run-'+uuid.uuid4().hex[:12]);out.mkdir(parents=True)
    return Seed(root,out,password,args.include_qa02_refunds).run()
if __name__=='__main__':raise SystemExit(main())

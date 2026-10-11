import type { User, Card, TransactionRecord, Page, Statistics, DashboardOverview, TransactionFilter, UserFilter, CreateUserRequest, UpdateUserRequest, OperationRequest } from './types'
export class ApiError extends Error {
 constructor(public code:string, message:string, public status=0) { super(message); this.name='ApiError' }
}
let sessionEpoch = 0
function resetSession() { ++sessionEpoch; csrf=null; csrfPromise=null }
let csrf: {token:string;headerName:string}|null = null
let csrfPromise: Promise<{token:string;headerName:string}>|null = null
async function csrfToken(force=false) {
 if(force) { csrf=null; csrfPromise=null }
 if(csrf) return csrf
 if(!csrfPromise) {
  const epoch=sessionEpoch
  const pending=fetch('/api/auth/csrf',{credentials:'same-origin',cache:'no-store'})
   .then(async r=>{if(!r.ok) throw new ApiError('NETWORK_ERROR','暂时无法连接服务，请稍后再试',r.status); return await r.json() as {token:string;headerName:string}})
   .then(value=>{if(epoch!==sessionEpoch) throw new ApiError('SESSION_CHANGED','登录状态已更改，请重新操作'); csrf=value;return value})
   .catch(e=>{if(e instanceof ApiError)throw e;throw new ApiError('NETWORK_ERROR','无法连接服务，请检查网络')})
   .finally(()=>{if(csrfPromise===pending)csrfPromise=null})
  csrfPromise=pending
 }
 return csrfPromise
}
export function queryString(params:object={}) {
 const query=new URLSearchParams()
 Object.entries(params).forEach(([key,value])=>{if(value!==undefined&&value!==null&&value!=='') query.set(key,String(value))})
 return query.size?'?'+query.toString():''
}
async function request<T>(path:string,method='GET',body?:unknown,asBlob=false,retried=false):Promise<T> {
 const epoch=sessionEpoch
 const headers: Record<string,string> = { Accept: asBlob?'text/csv':'application/json' }
 if(body!==undefined) headers['Content-Type']='application/json'
 if(method!=='GET') { const token=await csrfToken(); headers[token.headerName]=token.token }
 if(epoch!==sessionEpoch) throw new ApiError('SESSION_CHANGED','登录状态已更改，请重新操作')
 let response: Response
 try { response=await fetch('/api'+path,{method,headers,credentials:'same-origin',cache:'no-store',body:body===undefined?undefined:JSON.stringify(body)}) }
 catch { throw new ApiError('NETWORK_ERROR','无法连接服务，请检查连接后确认操作结果') }
 if(!response.ok) {
  const failure=await response.json().catch(()=>({code:'SERVER_ERROR',message:'服务暂时不可用'}))
  if(response.status===403&&failure.code==='CSRF_INVALID'&&!retried&&method!=='GET'&&epoch===sessionEpoch) { await csrfToken(true); if(epoch!==sessionEpoch)throw new ApiError('SESSION_CHANGED','登录状态已更改，请重新操作'); return request<T>(path,method,body,asBlob,true) }
  if(((response.status===401&&failure.code!=='LOGIN_FAILED')||failure.code==='USER_DISABLED')&&path!=='/auth/login'&&epoch===sessionEpoch) window.dispatchEvent(new CustomEvent('campus-session-expired'))
  throw new ApiError(failure.code||'SERVER_ERROR',failure.message||'操作失败，请稍后再试',response.status)
 }
 if(response.status===204) return undefined as T
 try {
  if(asBlob) return await response.blob() as T
  return await response.json() as T
 } catch { throw new ApiError('NETWORK_ERROR','响应未完整接收，请查询并确认操作结果') }
}
export const api = {
 me:()=>request<User>('/auth/me'),
 login:async(username:string,password:string)=>{resetSession();const user=await request<User>('/auth/login','POST',{username,password}); await csrfToken(true);return user},
 logout:async()=>{resetSession();try{await request<void>('/auth/logout','POST')}finally{csrf=null;csrfPromise=null}},
 changePassword:(oldPassword:string,newPassword:string)=>request<void>('/auth/password','POST',{oldPassword,newPassword}),
 card:()=>request<Card>('/card'),
 recharge:(operation:OperationRequest)=>request<TransactionRecord>('/card/recharge','POST',operation),
 consume:(operation:OperationRequest)=>request<TransactionRecord>('/card/consume','POST',operation),
 findOperation:(id:string)=>request<TransactionRecord|null>('/card/operations/'+encodeURIComponent(id)),
 records:(filter:TransactionFilter={})=>request<Page<TransactionRecord>>('/transactions'+queryString(filter)),
 exportRecords:(filter:TransactionFilter={})=>request<Blob>('/transactions/export'+queryString(filter),'GET',undefined,true),
 users:(filter:UserFilter={})=>request<Page<User>>('/admin/users'+queryString(filter)),
 createUser:(form:CreateUserRequest)=>request<User>('/admin/users','POST',form),
 updateUser:(id:number,form:UpdateUserRequest)=>request<User>('/admin/users/'+id,'PUT',form),
 setEnabled:(id:number,enabled:boolean)=>request<void>('/admin/users/'+id+'/enabled','PATCH',{enabled}),
 statistics:(from?:string,to?:string)=>request<Statistics>('/admin/statistics'+queryString({from,to})),
 dashboard:(days=7)=>request<DashboardOverview>('/dashboard'+queryString({days}))
}

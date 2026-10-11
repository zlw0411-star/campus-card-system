export type Role = 'USER' | 'ADMIN'
export type TransactionType = 'RECHARGE' | 'CONSUMPTION'
export interface User { id:number; username:string; fullName:string; studentNo:string|null; role:Role; enabled:boolean; createdAt:string }
export interface Card { id:number; userId:number; cardNo:string; balance:string }
export interface TransactionRecord { id:number; cardId:number; userId:number; username:string; fullName:string; type:TransactionType; amount:string; balanceBefore:string; balanceAfter:string; description:string; actorId:number; operationId:string; createdAt:string }
export interface Page<T> { items:T[]; total:number; page:number; pageSize:number }
export interface Statistics { users:number; enabledUsers:number; totalBalance:string; rechargeAmount:string; consumptionAmount:string; transactionCount:number }
export interface TrendPoint { date:string; rechargeAmount:string; consumptionAmount:string }
export interface DashboardOverview { rechargeAmount:string; consumptionAmount:string; transactionCount:number; trend:TrendPoint[] }
export interface TransactionFilter { userId?:number; type?:TransactionType; from?:string; to?:string; page?:number; pageSize?:number }
export interface UserFilter { keyword?:string; enabled?:boolean; page?:number; pageSize?:number }
export interface CreateUserRequest { username:string; password:string; fullName:string; studentNo:string }
export interface UpdateUserRequest { fullName:string; studentNo:string }
export interface OperationRequest { amount:string; description:string; operationId:string }

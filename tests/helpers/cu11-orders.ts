import { execFileSync } from 'node:child_process';

export interface Cu11Order { email:string; accountId:number; orderId:number; addressId:number; productId:number; transactionId:string; total:number; productName:string; status:string; }
/** Creates an explicitly labelled SQL fixture, never completes a UI checkout. */
export function cu11Order(options: {status?:string; mp?:boolean; createdAt?:string} = {}): Cu11Order {
  const args=['scripts/cu11-fixture.py','--email',process.env.E2E_EMAIL || 'cu11-ui@example.test'];
  if(options.status) args.push('--status',options.status);
  if(options.mp) args.push('--mp');
  if(options.createdAt) args.push('--created-at',options.createdAt);
  return JSON.parse(execFileSync('python3',args,{encoding:'utf8',timeout:75000}));
}

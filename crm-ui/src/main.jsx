import React, {useState} from 'react';
import {createRoot} from 'react-dom/client';
import './style.css';

const CRM_API = 'http://localhost:8081/api';

function App(){
  const [customerId,setCustomerId]=useState('CUST9001');
  const [firstName,setFirstName]=useState('Demo');
  const [lastName,setLastName]=useState('Customer');
  const [products,setProducts]=useState([
    {sourceProductCode:'1893',sourceItemRef:'REF-9001',productType:'TARIFF'},
    {sourceProductCode:'41001',sourceItemRef:'REF-9002',productType:'ADDON'}
  ]);
  const [result,setResult]=useState(null);
  const [error,setError]=useState(null);

  async function postJson(path, body){
    setError(null); setResult(null);
    const res = await fetch(CRM_API + path, {method:'POST', headers:{'Content-Type':'application/json'}, body:JSON.stringify(body)});
    const text = await res.text();
    if(!res.ok) throw new Error(text || res.statusText);
    return text ? JSON.parse(text) : {};
  }
  async function createCustomer(){
    try{ setResult(await postJson('/customers',{customerId,firstName,lastName})); }catch(e){setError(e.message)}
  }
  async function createOrder(){
    try{ setResult(await postJson('/orders',{customerId,products})); }catch(e){setError(e.message)}
  }
  function updateProduct(i,k,v){ setProducts(products.map((p,idx)=>idx===i?{...p,[k]:v}:p)); }
  return <main>
    <h1>CRM Order Console</h1>
    <section className="card"><h2>Customer</h2>
      <input value={customerId} onChange={e=>setCustomerId(e.target.value)} placeholder="Customer ID" />
      <input value={firstName} onChange={e=>setFirstName(e.target.value)} placeholder="First name" />
      <input value={lastName} onChange={e=>setLastName(e.target.value)} placeholder="Last name" />
      <button onClick={createCustomer}>Create Customer</button>
    </section>
    <section className="card"><h2>Product Order</h2>
      {products.map((p,i)=><div className="row" key={i}>
        <input value={p.sourceProductCode} onChange={e=>updateProduct(i,'sourceProductCode',e.target.value)} />
        <input value={p.sourceItemRef} onChange={e=>updateProduct(i,'sourceItemRef',e.target.value)} />
        <select value={p.productType} onChange={e=>updateProduct(i,'productType',e.target.value)}><option>TARIFF</option><option>CAMPAIGN</option><option>ADDON</option></select>
      </div>)}
      <button onClick={createOrder}>Submit Product Order</button>
    </section>
    {result && <pre className="ok">{JSON.stringify(result,null,2)}</pre>}
    {error && <pre className="err">{error}</pre>}
  </main>
}

createRoot(document.getElementById('root')).render(<App/>);

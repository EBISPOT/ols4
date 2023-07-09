
import express from 'express'
import fetch from 'node-fetch'
import urlJoin from 'url-join'
import nocache from 'nocache'


let server = express()

server.use(nocache())

if(process.env.OLS_DEV_BACKEND_PROXY_URL === undefined) {
    throw new Error('please set OLS_DEV_BACKEND_PROXY_URL before running dev server')
}
server.use(/^\/api.*/, async (req, res) => {
  let backendUrl = urlJoin(process.env.OLS_DEV_BACKEND_PROXY_URL, req.originalUrl)
  console.log('forwarding api request to: ' + backendUrl)
  let apiResponse = await fetch(backendUrl, {
    redirect: 'follow',
    method: req.method,
    body: req.body
  })
  res.header('content-type', apiResponse.headers.get('content-type'))
  res.status(apiResponse.status)
  apiResponse.body.pipe(res)
})


server.use(express.static('dist'))

server.get(/^(?!\/api).*$/, (req, res) => {
  res.sendFile(process.cwd() + '/dist/index.html')
})



    
server.listen(3000)    





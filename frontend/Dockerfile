# Build the front end first:
#   cd frontend && npm install && npm run build
# This image serves the built dist to keep the demo reproducible offline.
FROM nginx:1.27-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY dist /usr/share/nginx/html
EXPOSE 80

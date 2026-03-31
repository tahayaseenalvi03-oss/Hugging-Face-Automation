#Hugging Face Access Report Automation (Java + Selenium)

Automates extraction of user access data from Hugging Face gated repositories using Selenium browser automation.
This tool simulates manual login and navigates the Hugging Face UI to collect access requests and approved users, enabling structured reporting and analysis.
Hugging Face does not provide a public API for detailed access logs of gated repositories.

#This project solves that by:

1. Automating browser interactions
2. Logging in manually (secure, no credential storage)
3. Navigating to repository access settings
4. Extracting user access data directly from the UI

#How It Works

1. Launches browser using Selenium
2. User logs in manually to Hugging Face
3. Script navigates to gated repository settings

**Scrapes:**

1.Approved users
2. Pending access requests
3. Exports structured data (CSV / JSON)

**Key Use Cases**
1. Track who is accessing your datasets
2. Monitor access requests across repositories
3. Generate leads from dataset users
4. Automate repetitive manual checks

**Tech Stack**

1. Java
2. Selenium WebDriver
3. ChromeDriver (or compatible browser driver)

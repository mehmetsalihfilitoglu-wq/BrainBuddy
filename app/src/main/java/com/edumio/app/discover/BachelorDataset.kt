package com.edumio.app.discover

import com.edumio.app.core.CareerPath
import com.edumio.app.core.ExamType

/**
 * Auto-generated bachelor dataset merged from BACHELOR LISTt.xlsx (detail) and
 * BACHELOR.xlsx (category + type + coverage). Seed data: year-to-year fields
 * (dates, fees, quotas) are guidance only, paired with the official-notice caveat.
 */
object BachelorDataset {
    val programs: List<BachelorProgram> = listOf(
        BachelorProgram(
            id = "polito_torino__mechanical_engineering", universityId = "polito_torino", universityName = "Politecnico di Torino",
            city = "Torino", region = "Piemonte",
            programName = "Mechanical Engineering", fieldCategory = FieldCategory.ENGINEERING,
            relatedCareers = listOf(CareerPath.ENGINEERING, CareerPath.COMPUTER_SCIENCE), primaryStudyArea = ExamType.TIL_I,
            admissionExam = "TIL-I", tags = listOf("TIL-I"), isPublic = true
        ),
        BachelorProgram(
            id = "polito_torino__computer_engineering", universityId = "polito_torino", universityName = "Politecnico di Torino",
            city = "Torino", region = "Piemonte",
            programName = "Computer Engineering", fieldCategory = FieldCategory.ENGINEERING,
            relatedCareers = listOf(CareerPath.ENGINEERING, CareerPath.COMPUTER_SCIENCE), primaryStudyArea = ExamType.TIL_I,
            admissionExam = "TIL-I", tags = listOf("TIL-I"), isPublic = true
        ),
        BachelorProgram(
            id = "polito_torino__automotive_engineering_l_9", universityId = "polito_torino", universityName = "Politecnico di Torino",
            city = "Torino", region = "Piemonte",
            programName = "Automotive Engineering – L-9", fieldCategory = FieldCategory.ENGINEERING,
            relatedCareers = listOf(CareerPath.ENGINEERING, CareerPath.COMPUTER_SCIENCE), primaryStudyArea = ExamType.TIL_I,
            admissionExam = "TIL-I", tags = listOf("TIL-I", "SAT (alternatif)"), isPublic = true
        ),
        BachelorProgram(
            id = "polito_torino__electronic_and_communications_engineering", universityId = "polito_torino", universityName = "Politecnico di Torino",
            city = "Torino", region = "Piemonte",
            programName = "Electronic and Communications Engineering", fieldCategory = FieldCategory.ENGINEERING,
            relatedCareers = listOf(CareerPath.ENGINEERING, CareerPath.COMPUTER_SCIENCE), primaryStudyArea = ExamType.TIL_I,
            admissionExam = "TIL-I", tags = listOf("TIL-I"), isPublic = true
        ),
        BachelorProgram(
            id = "polito_torino__civil_and_envirnmental_engineering", universityId = "polito_torino", universityName = "Politecnico di Torino",
            city = "Torino", region = "Piemonte",
            programName = "Civil and Envirnmental Engineering", fieldCategory = FieldCategory.ENGINEERING,
            relatedCareers = listOf(CareerPath.ENGINEERING, CareerPath.COMPUTER_SCIENCE), primaryStudyArea = ExamType.TIL_I,
            admissionExam = "TIL-I", tags = listOf("TIL-I"), isPublic = true
        ),
        BachelorProgram(
            id = "polito_milano__industrial_engineering", universityId = "polito_milano", universityName = "Politecnico di Milano",
            city = "Milano", region = "Lombardia",
            programName = "Industrial Engineering", fieldCategory = FieldCategory.ENGINEERING,
            relatedCareers = listOf(CareerPath.ENGINEERING, CareerPath.COMPUTER_SCIENCE), primaryStudyArea = ExamType.TIL_I,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S", "SAT (alternatif)"), isPublic = true
        ),
        BachelorProgram(
            id = "polito_milano__process_engineering", universityId = "polito_milano", universityName = "Politecnico di Milano",
            city = "Milano", region = "Lombardia",
            programName = "Process Engineering", fieldCategory = FieldCategory.ENGINEERING,
            relatedCareers = listOf(CareerPath.ENGINEERING, CareerPath.COMPUTER_SCIENCE), primaryStudyArea = ExamType.TIL_I,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S", "SAT (alternatif)"), isPublic = true
        ),
        BachelorProgram(
            id = "polito_milano__engineering_science", universityId = "polito_milano", universityName = "Politecnico di Milano",
            city = "Milano", region = "Lombardia",
            programName = "Engineering Science", fieldCategory = FieldCategory.ENGINEERING,
            relatedCareers = listOf(CareerPath.ENGINEERING, CareerPath.COMPUTER_SCIENCE), primaryStudyArea = ExamType.TIL_I,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S", "SAT (alternatif)"), isPublic = true
        ),
        BachelorProgram(
            id = "polito_milano__civil_engineering_l_7", universityId = "polito_milano", universityName = "Politecnico di Milano",
            city = "Milano", region = "Lombardia",
            programName = "Civil Engineering – L-7", fieldCategory = FieldCategory.ENGINEERING,
            relatedCareers = listOf(CareerPath.ENGINEERING, CareerPath.COMPUTER_SCIENCE), primaryStudyArea = ExamType.TIL_I,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S", "SAT (alternatif)"), isPublic = true
        ),
        BachelorProgram(
            id = "trento__computer_science", universityId = "trento", universityName = "University of Trento",
            city = "Trento", region = "Trentino-Alto Adige",
            programName = "Computer Science", fieldCategory = FieldCategory.ENGINEERING,
            relatedCareers = listOf(CareerPath.ENGINEERING, CareerPath.COMPUTER_SCIENCE), primaryStudyArea = ExamType.TIL_I,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "trento__computer_communications_and_electronic_engineering", universityId = "trento", universityName = "University of Trento",
            city = "Trento", region = "Trentino-Alto Adige",
            programName = "Computer, Communications and Electronic Engineering", fieldCategory = FieldCategory.ENGINEERING,
            relatedCareers = listOf(CareerPath.ENGINEERING, CareerPath.COMPUTER_SCIENCE), primaryStudyArea = ExamType.TIL_I,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "pavia__artificial_intelligence", universityId = "pavia", universityName = "University of Pavia",
            city = "Pavia", region = "Lombardia",
            programName = "Artificial Intelligence", fieldCategory = FieldCategory.ENGINEERING,
            relatedCareers = listOf(CareerPath.ENGINEERING, CareerPath.COMPUTER_SCIENCE), primaryStudyArea = ExamType.TIL_I,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "messina__data_analysis", universityId = "messina", universityName = "University of Messina",
            city = "Messina", region = "Sicilia",
            programName = "Data Analysis", fieldCategory = FieldCategory.ENGINEERING,
            relatedCareers = listOf(CareerPath.ENGINEERING, CareerPath.COMPUTER_SCIENCE), primaryStudyArea = ExamType.TIL_I,
            admissionExam = "Belge Değerlendirme", tags = listOf("Belge Değerlendirme"), isPublic = true
        ),
        BachelorProgram(
            id = "ca_foscari__computer_science_data_science", universityId = "ca_foscari", universityName = "Ca' Foscari University of Venice",
            city = "Venezia", region = "Veneto",
            programName = "Computer Science - Data Science", fieldCategory = FieldCategory.ENGINEERING,
            relatedCareers = listOf(CareerPath.ENGINEERING, CareerPath.COMPUTER_SCIENCE), primaryStudyArea = ExamType.TIL_I,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "bocconi__mathematical_computing_sciences_for_ai", universityId = "bocconi", universityName = "Bocconi University",
            city = "Milano", region = "Lombardia",
            programName = "Mathematical & Computing Sciences for AI", fieldCategory = FieldCategory.ENGINEERING,
            relatedCareers = listOf(CareerPath.ENGINEERING, CareerPath.COMPUTER_SCIENCE), primaryStudyArea = ExamType.TIL_I,
            admissionExam = "SAT", tags = listOf("SAT"), isPublic = false
        ),
        BachelorProgram(
            id = "vanvitelli__data_analytics_l_41", universityId = "vanvitelli", universityName = "University of Campania Vanvitelli",
            city = "Caserta", region = "Campania",
            programName = "Data Analytics – L-41", fieldCategory = FieldCategory.ENGINEERING,
            relatedCareers = listOf(CareerPath.ENGINEERING, CareerPath.COMPUTER_SCIENCE), primaryStudyArea = ExamType.TIL_I,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "campus_biomedico__biomedical_engineering_l_8", universityId = "campus_biomedico", universityName = "Campus Bio-Medico University",
            city = "Roma", region = "Lazio",
            programName = "Biomedical Engineering – L-8", fieldCategory = FieldCategory.ENGINEERING,
            relatedCareers = listOf(CareerPath.ENGINEERING, CareerPath.COMPUTER_SCIENCE), primaryStudyArea = ExamType.TIL_I,
            admissionExam = "SAT", tags = listOf("SAT"), isPublic = false
        ),
        BachelorProgram(
            id = "padova__information_engineering", universityId = "padova", universityName = "University of Padua",
            city = "Padova", region = "Veneto",
            programName = "Information Engineering", fieldCategory = FieldCategory.ENGINEERING,
            relatedCareers = listOf(CareerPath.ENGINEERING, CareerPath.COMPUTER_SCIENCE), primaryStudyArea = ExamType.TIL_I,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "genova__computer_engineering", universityId = "genova", universityName = "University of Genoa",
            city = "Genova", region = "Liguria",
            programName = "Computer Engineering", fieldCategory = FieldCategory.ENGINEERING,
            relatedCareers = listOf(CareerPath.ENGINEERING, CareerPath.COMPUTER_SCIENCE), primaryStudyArea = ExamType.TIL_I,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "cassino__industrial_engineering_technology", universityId = "cassino", universityName = "University of Cassino",
            city = "Cassino", region = "Lazio",
            programName = "Industrial Engineering Technology", fieldCategory = FieldCategory.ENGINEERING,
            relatedCareers = listOf(CareerPath.ENGINEERING, CareerPath.COMPUTER_SCIENCE), primaryStudyArea = ExamType.TIL_I,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "tor_vergata__engineering_sciences", universityId = "tor_vergata", universityName = "University of Rome Tor Vergata",
            city = "Roma", region = "Lazio",
            programName = "Engineering Sciences", fieldCategory = FieldCategory.ENGINEERING,
            relatedCareers = listOf(CareerPath.ENGINEERING, CareerPath.COMPUTER_SCIENCE), primaryStudyArea = ExamType.TIL_I,
            admissionExam = "Üniversite Değerlendirmesi", tags = listOf("Üniversite Sınavı"), isPublic = true
        ),
        BachelorProgram(
            id = "sapienza__sustainable_building_engineering", universityId = "sapienza", universityName = "Sapienza University of Rome",
            city = "Rieti", region = "Lazio",
            programName = "Sustainable Building Engineering", fieldCategory = FieldCategory.ENGINEERING,
            relatedCareers = listOf(CareerPath.ENGINEERING, CareerPath.COMPUTER_SCIENCE), primaryStudyArea = ExamType.TIL_I,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S", "SAT (alternatif)"), isPublic = true
        ),
        BachelorProgram(
            id = "bologna__building_constructions_engineering", universityId = "bologna", universityName = "University of Bologna",
            city = "Ravenna", region = "Emilia-Romagna",
            programName = "Building Constructions Engineering", fieldCategory = FieldCategory.ENGINEERING,
            relatedCareers = listOf(CareerPath.ENGINEERING, CareerPath.COMPUTER_SCIENCE), primaryStudyArea = ExamType.TIL_I,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "messina__civil_engineering", universityId = "messina", universityName = "University of Messina",
            city = "Messina", region = "Sicilia",
            programName = "Civil Engineering", fieldCategory = FieldCategory.ENGINEERING,
            relatedCareers = listOf(CareerPath.ENGINEERING, CareerPath.COMPUTER_SCIENCE), primaryStudyArea = ExamType.TIL_I,
            admissionExam = "Belge Değerlendirme", tags = listOf("Belge Değerlendirme"), isPublic = true
        ),
        BachelorProgram(
            id = "polito_torino__architecture_l_17", universityId = "polito_torino", universityName = "Politecnico di Torino",
            city = "Torino", region = "Piemonte",
            programName = "Architecture – L-17", fieldCategory = FieldCategory.ARCHITECTURE_DESIGN,
            relatedCareers = listOf(CareerPath.ARCHITECTURE, CareerPath.DESIGN), primaryStudyArea = ExamType.TIL_A,
            admissionExam = "TIL-A / TEST-ARCHED", tags = listOf("Mimarlık Sınavı"), isPublic = true
        ),
        BachelorProgram(
            id = "polito_milano__architectural_design_l_17", universityId = "polito_milano", universityName = "Politecnico di Milano",
            city = "Milano", region = "Lombardia",
            programName = "Architectural Design – L-17", fieldCategory = FieldCategory.ARCHITECTURE_DESIGN,
            relatedCareers = listOf(CareerPath.ARCHITECTURE, CareerPath.DESIGN), primaryStudyArea = ExamType.TIL_A,
            admissionExam = "TIL-A / TEST-ARCHED", tags = listOf("Mimarlık Sınavı"), isPublic = true
        ),
        BachelorProgram(
            id = "polito_milano__interaction_design", universityId = "polito_milano", universityName = "Politecnico di Milano",
            city = "Milano", region = "Lombardia",
            programName = "Interaction Design", fieldCategory = FieldCategory.ARCHITECTURE_DESIGN,
            relatedCareers = listOf(CareerPath.ARCHITECTURE, CareerPath.DESIGN), primaryStudyArea = ExamType.TIL_A,
            admissionExam = "Üniversite Değerlendirmesi", tags = listOf("Üniversite Sınavı"), isPublic = true
        ),
        BachelorProgram(
            id = "polito_bari__architecture_sciences_for_heritage_l_17", universityId = "polito_bari", universityName = "Politecnico di Bari",
            city = "Bari", region = "Puglia",
            programName = "Architecture Sciences for Heritage – L-17", fieldCategory = FieldCategory.ARCHITECTURE_DESIGN,
            relatedCareers = listOf(CareerPath.ARCHITECTURE, CareerPath.DESIGN), primaryStudyArea = ExamType.TIL_A,
            admissionExam = "TIL-A / TEST-ARCHED", tags = listOf("Mimarlık Sınavı"), isPublic = true
        ),
        BachelorProgram(
            id = "domus__fashion_design", universityId = "domus", universityName = "Domus Academy",
            city = "Milano", region = "Lombardia",
            programName = "Fashion Design", fieldCategory = FieldCategory.ARCHITECTURE_DESIGN,
            relatedCareers = listOf(CareerPath.ARCHITECTURE, CareerPath.DESIGN), primaryStudyArea = ExamType.TIL_A,
            admissionExam = "Portfolyo + Mülakat", tags = listOf("Portfolyo"), isPublic = false
        ),
        BachelorProgram(
            id = "domus__design", universityId = "domus", universityName = "Domus Academy",
            city = "Milano", region = "Lombardia",
            programName = "Design", fieldCategory = FieldCategory.ARCHITECTURE_DESIGN,
            relatedCareers = listOf(CareerPath.ARCHITECTURE, CareerPath.DESIGN), primaryStudyArea = ExamType.TIL_A,
            admissionExam = "Portfolyo + Mülakat", tags = listOf("Portfolyo"), isPublic = false
        ),
        BachelorProgram(
            id = "ied__graphic_design", universityId = "ied", universityName = "Istituto Europeo di Design",
            city = "Milano", region = "Lombardia",
            programName = "Graphic Design", fieldCategory = FieldCategory.ARCHITECTURE_DESIGN,
            relatedCareers = listOf(CareerPath.ARCHITECTURE, CareerPath.DESIGN), primaryStudyArea = ExamType.TIL_A,
            admissionExam = "Portfolyo + Mülakat", tags = listOf("Portfolyo"), isPublic = false
        ),
        BachelorProgram(
            id = "ied__jewelry_design", universityId = "ied", universityName = "Istituto Europeo di Design",
            city = "Milano", region = "Lombardia",
            programName = "Jewelry Design", fieldCategory = FieldCategory.ARCHITECTURE_DESIGN,
            relatedCareers = listOf(CareerPath.ARCHITECTURE, CareerPath.DESIGN), primaryStudyArea = ExamType.TIL_A,
            admissionExam = "Portfolyo + Mülakat", tags = listOf("Portfolyo"), isPublic = false
        ),
        BachelorProgram(
            id = "ied__interior_design", universityId = "ied", universityName = "Istituto Europeo di Design",
            city = "Milano", region = "Lombardia",
            programName = "Interior Design", fieldCategory = FieldCategory.ARCHITECTURE_DESIGN,
            relatedCareers = listOf(CareerPath.ARCHITECTURE, CareerPath.DESIGN), primaryStudyArea = ExamType.TIL_A,
            admissionExam = "Portfolyo + Mülakat", tags = listOf("Portfolyo"), isPublic = false
        ),
        BachelorProgram(
            id = "ied__fashion_design", universityId = "ied", universityName = "Istituto Europeo di Design",
            city = "Milano", region = "Lombardia",
            programName = "Fashion Design", fieldCategory = FieldCategory.ARCHITECTURE_DESIGN,
            relatedCareers = listOf(CareerPath.ARCHITECTURE, CareerPath.DESIGN), primaryStudyArea = ExamType.TIL_A,
            admissionExam = "Portfolyo + Mülakat", tags = listOf("Portfolyo"), isPublic = false
        ),
        BachelorProgram(
            id = "marche__digital_economics_and_business", universityId = "marche", universityName = "Marche Polytechnic University",
            city = "Ancona", region = "Marche",
            programName = "Digital Economics and Business", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "Belge Değerlendirme", tags = listOf("Belge Değerlendirme"), isPublic = true
        ),
        BachelorProgram(
            id = "pisa__management_for_business_and_economics", universityId = "pisa", universityName = "University of Pisa",
            city = "Pisa", region = "Toscana",
            programName = "Management for Business and Economics", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "torino__economics_and_finance_with_data_science", universityId = "torino", universityName = "University of Turin",
            city = "Torino", region = "Piemonte",
            programName = "Economics and Finance with Data Science", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "SAT", tags = listOf("SAT"), isPublic = true
        ),
        BachelorProgram(
            id = "torino__business_management", universityId = "torino", universityName = "University of Turin",
            city = "Torino", region = "Piemonte",
            programName = "Business & Management", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "SAT", tags = listOf("SAT"), isPublic = true
        ),
        BachelorProgram(
            id = "siena__economics_and_management_l_18_l_33", universityId = "siena", universityName = "University of Siena",
            city = "Siena", region = "Toscana",
            programName = "Economics and Management – L-18 / L-33", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "siena__economics_and_business", universityId = "siena", universityName = "University of Siena",
            city = "Siena", region = "Toscana",
            programName = "Economics and Business", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "SAT", tags = listOf("SAT"), isPublic = true
        ),
        BachelorProgram(
            id = "tor_vergata__business_administration_economics", universityId = "tor_vergata", universityName = "University of Rome Tor Vergata",
            city = "Roma", region = "Lazio",
            programName = "Business Administration & Economics", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "sapienza__economics_and_finance", universityId = "sapienza", universityName = "Sapienza University of Rome",
            city = "Roma", region = "Lazio",
            programName = "Economics and Finance", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S", "SAT (alternatif)"), isPublic = true
        ),
        BachelorProgram(
            id = "sapienza__business_sciences", universityId = "sapienza", universityName = "Sapienza University of Rome",
            city = "Roma", region = "Lazio",
            programName = "Business Sciences", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S", "SAT (alternatif)"), isPublic = true
        ),
        BachelorProgram(
            id = "padova__philosophy_politics_and_economics", universityId = "padova", universityName = "University of Padua",
            city = "Padova", region = "Veneto",
            programName = "Philosophy, Politics and Economics", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "napoli_parthenope__business_administration_l_18", universityId = "napoli_parthenope", universityName = "University of Naples Parthenope",
            city = "Napoli", region = "Campania",
            programName = "Business Administration – L-18", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "milano__economics_behavior_data_and_policy", universityId = "milano", universityName = "University of Milan",
            city = "Milano", region = "Lombardia",
            programName = "Economics, Behavior, Data and Policy", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "messina__business_management", universityId = "messina", universityName = "University of Messina",
            city = "Messina", region = "Sicilia",
            programName = "Business Management", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "Belge Değerlendirme", tags = listOf("Belge Değerlendirme"), isPublic = true
        ),
        BachelorProgram(
            id = "firenze__sustainable_business_for_societal_challenges", universityId = "firenze", universityName = "University of Florence",
            city = "Firenze", region = "Toscana",
            programName = "Sustainable Business for Societal Challenges", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "cassino__economics_with_data_science", universityId = "cassino", universityName = "University of Cassino",
            city = "Cassino", region = "Lazio",
            programName = "Economics with Data Science", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "brescia__business_and_economics", universityId = "brescia", universityName = "University of Brescia",
            city = "Brescia", region = "Lombardia",
            programName = "Business and Economics", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "bologna__management_and_economics", universityId = "bologna", universityName = "University of Bologna",
            city = "Forlì", region = "Emilia-Romagna",
            programName = "Management and Economics", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "SAT", tags = listOf("SAT"), isPublic = true
        ),
        BachelorProgram(
            id = "bologna__economics_of_tourism_and_cities", universityId = "bologna", universityName = "University of Bologna",
            city = "Rimini", region = "Emilia-Romagna",
            programName = "Economics of Tourism and Cities", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "SAT", tags = listOf("SAT"), isPublic = true
        ),
        BachelorProgram(
            id = "bologna__business_economics_l_18", universityId = "bologna", universityName = "University of Bologna",
            city = "Bologna", region = "Emilia-Romagna",
            programName = "Business Economics – L-18", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "bologna__economics_and_finance", universityId = "bologna", universityName = "University of Bologna",
            city = "Bologna", region = "Emilia-Romagna",
            programName = "Economics and Finance", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "SAT", tags = listOf("SAT"), isPublic = true
        ),
        BachelorProgram(
            id = "bologna__economics_politics_and_social_sciences", universityId = "bologna", universityName = "University of Bologna",
            city = "Rimini", region = "Emilia-Romagna",
            programName = "Economics, Politics and Social Sciences", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "SAT", tags = listOf("SAT"), isPublic = true
        ),
        BachelorProgram(
            id = "bocconi__international_economics_and_management_l_18", universityId = "bocconi", universityName = "Bocconi University",
            city = "Milano", region = "Lombardia",
            programName = "International Economics and Management – L-18", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "SAT", tags = listOf("SAT"), isPublic = false
        ),
        BachelorProgram(
            id = "bocconi__international_economics_and_finance_l_33", universityId = "bocconi", universityName = "Bocconi University",
            city = "Milano", region = "Lombardia",
            programName = "International Economics and Finance – L-33", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "SAT", tags = listOf("SAT"), isPublic = false
        ),
        BachelorProgram(
            id = "bocconi__economics_management_and_computer_science", universityId = "bocconi", universityName = "Bocconi University",
            city = "Milano", region = "Lombardia",
            programName = "Economics, Management and Computer Science", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "SAT", tags = listOf("SAT"), isPublic = false
        ),
        BachelorProgram(
            id = "bocconi__economic_and_social_sciences_l_33", universityId = "bocconi", universityName = "Bocconi University",
            city = "Milano", region = "Lombardia",
            programName = "Economic and Social Sciences – L-33", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "SAT", tags = listOf("SAT"), isPublic = false
        ),
        BachelorProgram(
            id = "bocconi__economics_and_management_for_arts_culture_and_communication", universityId = "bocconi", universityName = "Bocconi University",
            city = "Milano", region = "Lombardia",
            programName = "Economics and Management for Arts, Culture and Communication", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "SAT", tags = listOf("SAT"), isPublic = false
        ),
        BachelorProgram(
            id = "bocconi__world_bachelor_in_business", universityId = "bocconi", universityName = "Bocconi University",
            city = "Milano", region = "Lombardia",
            programName = "World Bachelor in Business", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "SAT", tags = listOf("SAT"), isPublic = false
        ),
        BachelorProgram(
            id = "cattolica__finance", universityId = "cattolica", universityName = "Università Cattolica del Sacro Cuore",
            city = "Milano", region = "Lombardia",
            programName = "Finance", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "SAT", tags = listOf("SAT"), isPublic = false
        ),
        BachelorProgram(
            id = "cattolica__business_and_finance_l_18", universityId = "cattolica", universityName = "Università Cattolica del Sacro Cuore",
            city = "Milano", region = "Lombardia",
            programName = "Business and Finance – L-18", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "SAT", tags = listOf("SAT"), isPublic = false
        ),
        BachelorProgram(
            id = "cattolica__economics_and_management_l_18", universityId = "cattolica", universityName = "Università Cattolica del Sacro Cuore",
            city = "Milano", region = "Lombardia",
            programName = "Economics and Management – L-18", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "SAT", tags = listOf("SAT"), isPublic = false
        ),
        BachelorProgram(
            id = "cattolica__economics_and_management_communication_and_society", universityId = "cattolica", universityName = "Università Cattolica del Sacro Cuore",
            city = "Milano", region = "Lombardia",
            programName = "Economics and Management Communication and Society", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "SAT", tags = listOf("SAT"), isPublic = false
        ),
        BachelorProgram(
            id = "liuc__business_administration_and_management_l_18", universityId = "liuc", universityName = "LIUC – Università Cattaneo",
            city = "Castellanza", region = "Lombardia",
            programName = "Business Administration and Management – L-18", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "Üniversite Değerlendirmesi", tags = listOf("Üniversite Sınavı"), isPublic = false
        ),
        BachelorProgram(
            id = "ca_foscari__economics_and_business_economics_markets_and_finance_curriculum", universityId = "ca_foscari", universityName = "Ca' Foscari University of Venice",
            city = "Venezia", region = "Veneto",
            programName = "Economics and Business - Economics, Markets and Finance Curriculum", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "ca_foscari__digital_management", universityId = "ca_foscari", universityName = "Ca' Foscari University of Venice",
            city = "Treviso", region = "Veneto",
            programName = "Digital Management", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "Üniversite Değerlendirmesi", tags = listOf("Üniversite Sınavı"), isPublic = true
        ),
        BachelorProgram(
            id = "ca_foscari__business_administration_business_administration_and_management", universityId = "ca_foscari", universityName = "Ca' Foscari University of Venice",
            city = "Venezia", region = "Veneto",
            programName = "Business Administration - Business Administration and Management", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "luiss__business_administration_l_18", universityId = "luiss", universityName = "LUISS Guido Carli",
            city = "Roma", region = "Lazio",
            programName = "Business Administration – L-18", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "SAT", tags = listOf("SAT"), isPublic = false
        ),
        BachelorProgram(
            id = "luiss__economics_and_business_l_33", universityId = "luiss", universityName = "LUISS Guido Carli",
            city = "Roma", region = "Lazio",
            programName = "Economics and Business – L-33", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "SAT", tags = listOf("SAT"), isPublic = false
        ),
        BachelorProgram(
            id = "luiss__management_and_computer_science_l_18", universityId = "luiss", universityName = "LUISS Guido Carli",
            city = "Roma", region = "Lazio",
            programName = "Management and Computer Science – L-18", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "SAT", tags = listOf("SAT"), isPublic = false
        ),
        BachelorProgram(
            id = "luiss__management_and_artificial_intelligence", universityId = "luiss", universityName = "LUISS Guido Carli",
            city = "Roma", region = "Lazio",
            programName = "Management and Artificial Intelligence", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "SAT", tags = listOf("SAT"), isPublic = false
        ),
        BachelorProgram(
            id = "sapienza__nursing_l_snt1", universityId = "sapienza", universityName = "Sapienza University of Rome",
            city = "Roma", region = "Lazio",
            programName = "Nursing – L/SNT1", fieldCategory = FieldCategory.HEALTH_SCIENCES,
            relatedCareers = listOf(CareerPath.PHARMACY), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "Ulusal Sağlık Sınavı", tags = listOf("Sağlık Sınavı"), isPublic = true
        ),
        BachelorProgram(
            id = "palermo__nursing_l_snt1", universityId = "palermo", universityName = "University of Palermo",
            city = "Palermo", region = "Sicilia",
            programName = "Nursing – L/SNT1", fieldCategory = FieldCategory.HEALTH_SCIENCES,
            relatedCareers = listOf(CareerPath.PHARMACY), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "Ulusal Sağlık Sınavı", tags = listOf("Sağlık Sınavı"), isPublic = true
        ),
        BachelorProgram(
            id = "vanvitelli__nursing_l_snt1", universityId = "vanvitelli", universityName = "University of Campania Vanvitelli",
            city = "Caserta", region = "Campania",
            programName = "Nursing – L/SNT1", fieldCategory = FieldCategory.HEALTH_SCIENCES,
            relatedCareers = listOf(CareerPath.PHARMACY), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "Ulusal Sağlık Sınavı", tags = listOf("Sağlık Sınavı"), isPublic = true
        ),
        BachelorProgram(
            id = "parma__dental_hygiene_l_snt3", universityId = "parma", universityName = "University of Parma",
            city = "Parma", region = "Emilia-Romagna",
            programName = "Dental Hygiene – L/SNT3", fieldCategory = FieldCategory.HEALTH_SCIENCES,
            relatedCareers = listOf(CareerPath.PHARMACY), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "Ulusal Sağlık Sınavı", tags = listOf("Sağlık Sınavı"), isPublic = true
        ),
        BachelorProgram(
            id = "unicamillus__nursing_l_snt1", universityId = "unicamillus", universityName = "UniCamillus",
            city = "Roma", region = "Lazio",
            programName = "Nursing – L/SNT1", fieldCategory = FieldCategory.HEALTH_SCIENCES,
            relatedCareers = listOf(CareerPath.PHARMACY), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "UniCamillus Test", tags = listOf(), isPublic = false
        ),
        BachelorProgram(
            id = "unicamillus__radiology_diagnostic_imaging_and_radiotherapy_techniques", universityId = "unicamillus", universityName = "UniCamillus",
            city = "Roma", region = "Lazio",
            programName = "Radiology, Diagnostic Imaging and Radiotherapy Techniques", fieldCategory = FieldCategory.HEALTH_SCIENCES,
            relatedCareers = listOf(CareerPath.PHARMACY), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "UniCamillus Test", tags = listOf(), isPublic = false
        ),
        BachelorProgram(
            id = "torino__global_law_and_transnational_legal_studies", universityId = "torino", universityName = "University of Turin",
            city = "Torino", region = "Piemonte",
            programName = "Global Law and Transnational Legal Studies", fieldCategory = FieldCategory.LAW_INTERNATIONAL,
            relatedCareers = listOf(CareerPath.LAW), primaryStudyArea = ExamType.TOLC_SU,
            admissionExam = "TOLC-SU", note = "TOLC-SU sınavının yalnızca İngilizce bölümü değerlendirmeye alınır.", tags = listOf("TOLC-SU"), isPublic = true
        ),
        BachelorProgram(
            id = "trento__comparative_european_and_international_legal_studies_ceils", universityId = "trento", universityName = "University of Trento",
            city = "Trento", region = "Trentino-Alto Adige",
            programName = "Comparative, European and International Legal Studies - CEILS", fieldCategory = FieldCategory.LAW_INTERNATIONAL,
            relatedCareers = listOf(CareerPath.LAW), primaryStudyArea = ExamType.TOLC_SU,
            admissionExam = "Üniversite Değerlendirmesi", tags = listOf("Üniversite Sınavı"), isPublic = true
        ),
        BachelorProgram(
            id = "tor_vergata__global_governance", universityId = "tor_vergata", universityName = "University of Rome Tor Vergata",
            city = "Roma", region = "Lazio",
            programName = "Global Governance", fieldCategory = FieldCategory.LAW_INTERNATIONAL,
            relatedCareers = listOf(CareerPath.LAW), primaryStudyArea = ExamType.TOLC_SU,
            admissionExam = "Üniversite Değerlendirmesi", tags = listOf("Üniversite Sınavı"), isPublic = true
        ),
        BachelorProgram(
            id = "milano__international_politics_law_and_economics", universityId = "milano", universityName = "University of Milan",
            city = "Milano", region = "Lombardia",
            programName = "International Politics, Law and Economics", fieldCategory = FieldCategory.LAW_INTERNATIONAL,
            relatedCareers = listOf(CareerPath.LAW), primaryStudyArea = ExamType.TOLC_SU,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "messina__transnational_and_european_legal_studies", universityId = "messina", universityName = "University of Messina",
            city = "Messina", region = "Sicilia",
            programName = "Transnational and European Legal Studies", fieldCategory = FieldCategory.LAW_INTERNATIONAL,
            relatedCareers = listOf(CareerPath.LAW), primaryStudyArea = ExamType.TOLC_SU,
            admissionExam = "Belge Değerlendirme", tags = listOf("Belge Değerlendirme"), isPublic = true
        ),
        BachelorProgram(
            id = "messina__political_sciences_and_international_relations", universityId = "messina", universityName = "University of Messina",
            city = "Messina", region = "Sicilia",
            programName = "Political Sciences and International Relations", fieldCategory = FieldCategory.LAW_INTERNATIONAL,
            relatedCareers = listOf(CareerPath.LAW), primaryStudyArea = ExamType.TOLC_SU,
            admissionExam = "Belge Değerlendirme", tags = listOf("Belge Değerlendirme"), isPublic = true
        ),
        BachelorProgram(
            id = "macerata__international_european_and_comparative_legal_studies", universityId = "macerata", universityName = "University of Macerata",
            city = "Macerata", region = "Marche",
            programName = "International, European and Comparative Legal Studies", fieldCategory = FieldCategory.LAW_INTERNATIONAL,
            relatedCareers = listOf(CareerPath.LAW), primaryStudyArea = ExamType.TOLC_SU,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "bologna__international_studies", universityId = "bologna", universityName = "University of Bologna",
            city = "Forlì", region = "Emilia-Romagna",
            programName = "International Studies", fieldCategory = FieldCategory.LAW_INTERNATIONAL,
            relatedCareers = listOf(CareerPath.LAW), primaryStudyArea = ExamType.TOLC_SU,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "bocconi__global_law_l_14", universityId = "bocconi", universityName = "Bocconi University",
            city = "Milano", region = "Lombardia",
            programName = "Global Law – L-14", fieldCategory = FieldCategory.LAW_INTERNATIONAL,
            relatedCareers = listOf(CareerPath.LAW), primaryStudyArea = ExamType.TOLC_SU,
            admissionExam = "SAT", tags = listOf("SAT"), isPublic = false
        ),
        BachelorProgram(
            id = "bocconi__international_politics_and_government_l_36", universityId = "bocconi", universityName = "Bocconi University",
            city = "Milano", region = "Lombardia",
            programName = "International Politics and Government – L-36", fieldCategory = FieldCategory.LAW_INTERNATIONAL,
            relatedCareers = listOf(CareerPath.LAW), primaryStudyArea = ExamType.TOLC_SU,
            admissionExam = "SAT", tags = listOf("SAT"), isPublic = false
        ),
        BachelorProgram(
            id = "luiss__global_law", universityId = "luiss", universityName = "LUISS Guido Carli",
            city = "Roma", region = "Lazio",
            programName = "Global Law", fieldCategory = FieldCategory.LAW_INTERNATIONAL,
            relatedCareers = listOf(CareerPath.LAW), primaryStudyArea = ExamType.TOLC_SU,
            admissionExam = "SAT", tags = listOf("SAT"), isPublic = false
        ),
        BachelorProgram(
            id = "luiss__politics_philosophy_and_economics_l_36", universityId = "luiss", universityName = "LUISS Guido Carli",
            city = "Roma", region = "Lazio",
            programName = "Politics: Philosophy and Economics – L-36", fieldCategory = FieldCategory.LAW_INTERNATIONAL,
            relatedCareers = listOf(CareerPath.LAW), primaryStudyArea = ExamType.TOLC_SU,
            admissionExam = "SAT", tags = listOf("SAT"), isPublic = false
        ),
        BachelorProgram(
            id = "cattolica__international_relations_and_global_affairs", universityId = "cattolica", universityName = "Università Cattolica del Sacro Cuore",
            city = "Milano", region = "Lombardia",
            programName = "International Relations and Global Affairs", fieldCategory = FieldCategory.LAW_INTERNATIONAL,
            relatedCareers = listOf(CareerPath.LAW), primaryStudyArea = ExamType.TOLC_SU,
            admissionExam = "SAT", tags = listOf("SAT"), isPublic = false
        ),
        BachelorProgram(
            id = "cattolica__political_sciences_and_international_relations_l_36", universityId = "cattolica", universityName = "Università Cattolica del Sacro Cuore",
            city = "Milano", region = "Lombardia",
            programName = "Political Sciences and International Relations – L-36", fieldCategory = FieldCategory.LAW_INTERNATIONAL,
            relatedCareers = listOf(CareerPath.LAW), primaryStudyArea = ExamType.TOLC_SU,
            admissionExam = "SAT", tags = listOf("SAT"), isPublic = false
        ),
        BachelorProgram(
            id = "link_campus__media_and_digital_innovation_l_20", universityId = "link_campus", universityName = "Link Campus University",
            city = "Roma", region = "Lazio",
            programName = "Media and Digital Innovation – L-20", fieldCategory = FieldCategory.COMMUNICATION_MEDIA,
            relatedCareers = listOf(CareerPath.OTHER), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "Üniversite Değerlendirmesi", tags = listOf("Üniversite Sınavı"), isPublic = false
        ),
        BachelorProgram(
            id = "iulm__corporate_communication_and_public_relations_l_20", universityId = "iulm", universityName = "IULM University",
            city = "Milano", region = "Lombardia",
            programName = "Corporate Communication and Public Relations – L-20", fieldCategory = FieldCategory.COMMUNICATION_MEDIA,
            relatedCareers = listOf(CareerPath.OTHER), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "Üniversite Değerlendirmesi", tags = listOf("Üniversite Sınavı"), isPublic = false
        ),
        BachelorProgram(
            id = "cattolica__communication_management", universityId = "cattolica", universityName = "Università Cattolica del Sacro Cuore",
            city = "Milano", region = "Lombardia",
            programName = "Communication Management", fieldCategory = FieldCategory.COMMUNICATION_MEDIA,
            relatedCareers = listOf(CareerPath.OTHER), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "SAT", tags = listOf("SAT"), isPublic = false
        ),
        BachelorProgram(
            id = "ca_foscari__hospitality_innovation_and_e_tourism", universityId = "ca_foscari", universityName = "Ca' Foscari University of Venice",
            city = "Venezia", region = "Veneto",
            programName = "Hospitality Innovation and e-Tourism", fieldCategory = FieldCategory.COMMUNICATION_MEDIA,
            relatedCareers = listOf(CareerPath.OTHER), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S", "SAT (alternatif)"), isPublic = true
        ),
        BachelorProgram(
            id = "marche__environmental_sciences_and_civil_protection", universityId = "marche", universityName = "Marche Polytechnic University",
            city = "Ancona", region = "Marche",
            programName = "Environmental Sciences and Civil Protection", fieldCategory = FieldCategory.SCIENCE_BIOTECH,
            relatedCareers = listOf(CareerPath.BIOLOGY), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "pisa__geology_l_34", universityId = "pisa", universityName = "University of Pisa",
            city = "Pisa", region = "Toscana",
            programName = "Geology – L-34", fieldCategory = FieldCategory.SCIENCE_BIOTECH,
            relatedCareers = listOf(CareerPath.BIOLOGY), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "padova__earth_and_climate_dynamics", universityId = "padova", universityName = "University of Padua",
            city = "Padova", region = "Veneto",
            programName = "Earth and Climate Dynamics", fieldCategory = FieldCategory.SCIENCE_BIOTECH,
            relatedCareers = listOf(CareerPath.BIOLOGY), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "milano_bicocca__physical_sciences_for_innovative_technologies", universityId = "milano_bicocca", universityName = "University of Milan-Bicocca",
            city = "Milano", region = "Lombardia",
            programName = "Physical Sciences for Innovative Technologies", fieldCategory = FieldCategory.SCIENCE_BIOTECH,
            relatedCareers = listOf(CareerPath.BIOLOGY), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "milano_bicocca__economics_and_science_for_environmental_sustainability", universityId = "milano_bicocca", universityName = "University of Milan-Bicocca",
            city = "Milano", region = "Lombardia",
            programName = "Economics and Science for Environmental Sustainability", fieldCategory = FieldCategory.SCIENCE_BIOTECH,
            relatedCareers = listOf(CareerPath.BIOLOGY), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "teramo__biotechnology", universityId = "teramo", universityName = "University of Teramo",
            city = "Teramo", region = "Abruzzo",
            programName = "Biotechnology", fieldCategory = FieldCategory.SCIENCE_BIOTECH,
            relatedCareers = listOf(CareerPath.BIOLOGY), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "sapienza__bioinformatics_l_2", universityId = "sapienza", universityName = "Sapienza University of Rome",
            city = "Roma", region = "Lazio",
            programName = "Bioinformatics – L-2", fieldCategory = FieldCategory.SCIENCE_BIOTECH,
            relatedCareers = listOf(CareerPath.BIOLOGY), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "sapienza__molecular_biology_medicinal_chemistry_and_computer_science_for_pharmaceutical_ap", universityId = "sapienza", universityName = "Sapienza University of Rome",
            city = "Latina", region = "Lazio",
            programName = "Molecular Biology, Medicinal Chemistry  and Computer Science for Pharmaceutical  Applications", fieldCategory = FieldCategory.SCIENCE_BIOTECH,
            relatedCareers = listOf(CareerPath.BIOLOGY), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S", "SAT (alternatif)"), isPublic = true
        ),
        BachelorProgram(
            id = "padova__biology_of_human_and_environmental_health", universityId = "padova", universityName = "University of Padua",
            city = "Padova", region = "Veneto",
            programName = "Biology of Human and Environmental Health", fieldCategory = FieldCategory.SCIENCE_BIOTECH,
            relatedCareers = listOf(CareerPath.BIOLOGY), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "padova__animal_care", universityId = "padova", universityName = "University of Padua",
            city = "Padova", region = "Veneto",
            programName = "Animal Care", fieldCategory = FieldCategory.SCIENCE_BIOTECH,
            relatedCareers = listOf(CareerPath.BIOLOGY), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "messina__marine_biology_and_blue_biotechnologies", universityId = "messina", universityName = "University of Messina",
            city = "Messina", region = "Sicilia",
            programName = "Marine Biology and Blue Biotechnologies", fieldCategory = FieldCategory.SCIENCE_BIOTECH,
            relatedCareers = listOf(CareerPath.BIOLOGY), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "Belge Değerlendirme", tags = listOf("Belge Değerlendirme"), isPublic = true
        ),
        BachelorProgram(
            id = "camerino__biosciences_and_biotechnology", universityId = "camerino", universityName = "University of Camerino",
            city = "Camerino", region = "Marche",
            programName = "Biosciences and Biotechnology", fieldCategory = FieldCategory.SCIENCE_BIOTECH,
            relatedCareers = listOf(CareerPath.BIOLOGY), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "/", tags = listOf(), isPublic = true
        ),
        BachelorProgram(
            id = "bologna__genomics", universityId = "bologna", universityName = "University of Bologna",
            city = "Bologna", region = "Emilia-Romagna",
            programName = "Genomics", fieldCategory = FieldCategory.SCIENCE_BIOTECH,
            relatedCareers = listOf(CareerPath.BIOLOGY), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "pisa__foundation_course", universityId = "pisa", universityName = "University of Pisa",
            city = "Pisa", region = "Toscana",
            programName = "Foundation Course", fieldCategory = FieldCategory.FOUNDATION,
            relatedCareers = listOf(CareerPath.OTHER), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "Portfolyo + Mülakat", tags = listOf("Portfolyo"), isPublic = true
        ),
        BachelorProgram(
            id = "parma__foundation_course", universityId = "parma", universityName = "University of Parma",
            city = "Parma", region = "Emilia-Romagna",
            programName = "Foundation Course", fieldCategory = FieldCategory.FOUNDATION,
            relatedCareers = listOf(CareerPath.OTHER), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "Portfolyo + Mülakat", tags = listOf("Portfolyo"), isPublic = true
        ),
        BachelorProgram(
            id = "campus_biomedico__foundation_course", universityId = "campus_biomedico", universityName = "Campus Bio-Medico University",
            city = "Roma", region = "Lazio",
            programName = "Foundation Course", fieldCategory = FieldCategory.FOUNDATION,
            relatedCareers = listOf(CareerPath.OTHER), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "Üniversite Değerlendirmesi", tags = listOf("Üniversite Sınavı"), isPublic = false
        ),
        BachelorProgram(
            id = "ca_foscari__foundation_course", universityId = "ca_foscari", universityName = "Ca' Foscari University of Venice",
            city = "Venezia", region = "Veneto",
            programName = "Foundation Course", fieldCategory = FieldCategory.FOUNDATION,
            relatedCareers = listOf(CareerPath.OTHER), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "Üniversite Değerlendirmesi", tags = listOf("Üniversite Sınavı"), isPublic = true
        ),
        BachelorProgram(
            id = "luiss__foundation_course", universityId = "luiss", universityName = "LUISS Guido Carli",
            city = "Roma", region = "Lazio",
            programName = "Foundation Course", fieldCategory = FieldCategory.FOUNDATION,
            relatedCareers = listOf(CareerPath.OTHER), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "Üniversite Değerlendirmesi", tags = listOf("Üniversite Sınavı"), isPublic = false
        ),
        BachelorProgram(
            id = "link_campus__foundation_course", universityId = "link_campus", universityName = "Link Campus University",
            city = "Roma", region = "Lazio",
            programName = "Foundation Course", fieldCategory = FieldCategory.FOUNDATION,
            relatedCareers = listOf(CareerPath.OTHER), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "Üniversite Değerlendirmesi", tags = listOf("Üniversite Sınavı"), isPublic = false
        ),
        BachelorProgram(
            id = "link_campus__foundation_year", universityId = "link_campus", universityName = "Link Campus University",
            city = "Roma", region = "Lazio",
            programName = "Foundation Year", fieldCategory = FieldCategory.FOUNDATION,
            relatedCareers = listOf(CareerPath.OTHER), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "Üniversite Değerlendirmesi", tags = listOf("Üniversite Sınavı"), isPublic = false
        ),
        BachelorProgram(
            id = "bologna__international_relations_and_diplomatic_affairs", universityId = "bologna", universityName = "University of Bologna",
            city = "Bologna", region = "Emilia-Romagna",
            programName = "International Relations and Diplomatic Affairs", fieldCategory = FieldCategory.LAW_INTERNATIONAL,
            relatedCareers = listOf(CareerPath.LAW), primaryStudyArea = ExamType.TOLC_SU,
            admissionExam = "Güncel resmi duyuru kontrol edilmeli", tags = listOf(), isPublic = true
        ),
        BachelorProgram(
            id = "bologna__statistical_science", universityId = "bologna", universityName = "University of Bologna",
            city = "Bologna", region = "Emilia-Romagna",
            programName = "Statistical Science", fieldCategory = FieldCategory.SCIENCE_BIOTECH,
            relatedCareers = listOf(CareerPath.BIOLOGY), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "bologna__european_studies", universityId = "bologna", universityName = "University of Bologna",
            city = "Bologna", region = "Emilia-Romagna",
            programName = "European Studies", fieldCategory = FieldCategory.OTHER,
            relatedCareers = listOf(CareerPath.OTHER), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "bologna__pharmacy", universityId = "bologna", universityName = "University of Bologna",
            city = "Rimini", region = "Emilia-Romagna",
            programName = "Pharmacy", fieldCategory = FieldCategory.SCIENCE_BIOTECH,
            relatedCareers = listOf(CareerPath.BIOLOGY), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "ca_foscari__philosophy_international_and_economic_studies", universityId = "ca_foscari", universityName = "Ca' Foscari University of Venice",
            city = "Venezia", region = "Veneto",
            programName = "Philosophy, International and Economic Studies", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "cagliari__business_and_economics", universityId = "cagliari", universityName = "University of Cagliari",
            city = "Cagliari", region = "Sardegna",
            programName = "Business and Economics", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "messina__computer_science", universityId = "messina", universityName = "University of Messina",
            city = "Messina", region = "Sicilia",
            programName = "Computer Science", fieldCategory = FieldCategory.ENGINEERING,
            relatedCareers = listOf(CareerPath.ENGINEERING, CareerPath.COMPUTER_SCIENCE), primaryStudyArea = ExamType.TIL_I,
            admissionExam = "Belge Değerlendirme", tags = listOf("Belge Değerlendirme"), isPublic = true
        ),
        BachelorProgram(
            id = "napoli_federico__international_business_administration_and_management", universityId = "napoli_federico", universityName = "University of Naples Federico II",
            city = "Napoli", region = "Campania",
            programName = "International Business Administration and Management", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "Güncel resmi duyuru kontrol edilmeli", tags = listOf(), isPublic = true
        ),
        BachelorProgram(
            id = "napoli_federico__business_administration", universityId = "napoli_federico", universityName = "University of Naples Federico II",
            city = "Napoli", region = "Campania",
            programName = "Business Administration", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "Güncel resmi duyuru kontrol edilmeli", tags = listOf(), isPublic = true
        ),
        BachelorProgram(
            id = "padova__psychological_science", universityId = "padova", universityName = "University of Padua",
            city = "Padova", region = "Veneto",
            programName = "Psychological Science", fieldCategory = FieldCategory.PSYCHOLOGY_SOCIAL,
            relatedCareers = listOf(CareerPath.PSYCHOLOGY), primaryStudyArea = ExamType.TOLC_PSI,
            admissionExam = "UNIVERSITY EXAM (26.03.2025)", tags = listOf(), isPublic = true
        ),
        BachelorProgram(
            id = "padova__techniques_and_methods_in_psychological_science", universityId = "padova", universityName = "University of Padua",
            city = "Padova", region = "Veneto",
            programName = "Techniques and Methods in Psychological Science", fieldCategory = FieldCategory.PSYCHOLOGY_SOCIAL,
            relatedCareers = listOf(CareerPath.PSYCHOLOGY), primaryStudyArea = ExamType.TOLC_PSI,
            admissionExam = "UNIVERSITY EXAM (26.03.2025)", tags = listOf(), isPublic = true
        ),
        BachelorProgram(
            id = "pavia__social_sciences_for_global_challenges", universityId = "pavia", universityName = "University of Pavia",
            city = "Pavia", region = "Lombardia",
            programName = "Social Sciences for Global Challenges", fieldCategory = FieldCategory.SCIENCE_BIOTECH,
            relatedCareers = listOf(CareerPath.BIOLOGY), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "Belge Değerlendirme", tags = listOf("Belge Değerlendirme"), isPublic = true
        ),
        BachelorProgram(
            id = "perugia__engineering_management", universityId = "perugia", universityName = "University of Perugia",
            city = "Perugia", region = "Umbria",
            programName = "Engineering Management", fieldCategory = FieldCategory.ENGINEERING,
            relatedCareers = listOf(CareerPath.ENGINEERING, CareerPath.COMPUTER_SCIENCE), primaryStudyArea = ExamType.TIL_I,
            admissionExam = "/", tags = listOf(), isPublic = true
        ),
        BachelorProgram(
            id = "polito_torino__automative_engineering", universityId = "polito_torino", universityName = "Politecnico di Torino",
            city = "Torino", region = "Piemonte",
            programName = "Automative Engineering", fieldCategory = FieldCategory.ENGINEERING,
            relatedCareers = listOf(CareerPath.ENGINEERING, CareerPath.COMPUTER_SCIENCE), primaryStudyArea = ExamType.TIL_I,
            admissionExam = "TIL-I", tags = listOf("TIL-I"), isPublic = true
        ),
        BachelorProgram(
            id = "sapienza__global_humanities", universityId = "sapienza", universityName = "Sapienza University of Rome",
            city = "Roma", region = "Lazio",
            programName = "Global Humanities", fieldCategory = FieldCategory.HUMANITIES,
            relatedCareers = listOf(CareerPath.OTHER), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S", "SAT (alternatif)"), isPublic = true
        ),
        BachelorProgram(
            id = "sapienza__applied_computer_science_and_artificial_intelligence", universityId = "sapienza", universityName = "Sapienza University of Rome",
            city = "Roma", region = "Lazio",
            programName = "Applied Computer Science and Artificial  Intelligence", fieldCategory = FieldCategory.ENGINEERING,
            relatedCareers = listOf(CareerPath.ENGINEERING, CareerPath.COMPUTER_SCIENCE), primaryStudyArea = ExamType.TIL_I,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S", "SAT (alternatif)"), isPublic = true
        ),
        BachelorProgram(
            id = "sapienza__classics", universityId = "sapienza", universityName = "Sapienza University of Rome",
            city = "Roma", region = "Lazio",
            programName = "Classics", fieldCategory = FieldCategory.HUMANITIES,
            relatedCareers = listOf(CareerPath.OTHER), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S", "SAT (alternatif)"), isPublic = true
        ),
        BachelorProgram(
            id = "tor_vergata__pharmacy", universityId = "tor_vergata", universityName = "University of Rome Tor Vergata",
            city = "Roma", region = "Lazio",
            programName = "Pharmacy", fieldCategory = FieldCategory.SCIENCE_BIOTECH,
            relatedCareers = listOf(CareerPath.BIOLOGY), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "Üniversite Değerlendirmesi", tags = listOf("Üniversite Sınavı"), isPublic = true
        ),
        BachelorProgram(
            id = "sassari__computer_engineering", universityId = "sassari", universityName = "University of Sassari",
            city = "Sassari", region = "Sardegna",
            programName = "Computer Engineering", fieldCategory = FieldCategory.ENGINEERING,
            relatedCareers = listOf(CareerPath.ENGINEERING, CareerPath.COMPUTER_SCIENCE), primaryStudyArea = ExamType.TIL_I,
            admissionExam = "Güncel resmi duyuru kontrol edilmeli", tags = listOf(), isPublic = true
        ),
        BachelorProgram(
            id = "trento__economics_and_management", universityId = "trento", universityName = "University of Trento",
            city = "Trento", region = "Trentino-Alto Adige",
            programName = "Economics and Management", fieldCategory = FieldCategory.BUSINESS_ECONOMICS,
            relatedCareers = listOf(CareerPath.ECONOMICS), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S", "SAT (alternatif)"), isPublic = true
        ),
        BachelorProgram(
            id = "trento__computer_sciences", universityId = "trento", universityName = "University of Trento",
            city = "Trento", region = "Trentino-Alto Adige",
            programName = "Computer Sciences", fieldCategory = FieldCategory.ENGINEERING,
            relatedCareers = listOf(CareerPath.ENGINEERING, CareerPath.COMPUTER_SCIENCE), primaryStudyArea = ExamType.TIL_I,
            admissionExam = "CEnT-S", tags = listOf("CEnT-S"), isPublic = true
        ),
        BachelorProgram(
            id = "pisa__iph_cultural_heritage_studies_art_history_or_classical_archaeology", universityId = "pisa", universityName = "University of Pisa",
            city = "Pisa", region = "Toscana",
            programName = "IPH Cultural Heritage Studies (Art History or Classical Archaeology)", fieldCategory = FieldCategory.HUMANITIES,
            relatedCareers = listOf(CareerPath.OTHER), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "Üniversite Değerlendirmesi", tags = listOf("Üniversite Sınavı"), isPublic = true
        ),
        BachelorProgram(
            id = "pisa__iph_studies_in_performing_arts_and_communication", universityId = "pisa", universityName = "University of Pisa",
            city = "Pisa", region = "Toscana",
            programName = "IPH Studies in Performing Arts and Communication", fieldCategory = FieldCategory.COMMUNICATION_MEDIA,
            relatedCareers = listOf(CareerPath.OTHER), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "Üniversite Değerlendirmesi", tags = listOf("Üniversite Sınavı"), isPublic = true
        ),
        BachelorProgram(
            id = "pisa__iph_philosophy", universityId = "pisa", universityName = "University of Pisa",
            city = "Pisa", region = "Toscana",
            programName = "IPH Philosophy", fieldCategory = FieldCategory.HUMANITIES,
            relatedCareers = listOf(CareerPath.OTHER), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "Üniversite Değerlendirmesi", tags = listOf("Üniversite Sınavı"), isPublic = true
        ),
        BachelorProgram(
            id = "pisa__iph_history", universityId = "pisa", universityName = "University of Pisa",
            city = "Pisa", region = "Toscana",
            programName = "IPH History", fieldCategory = FieldCategory.HUMANITIES,
            relatedCareers = listOf(CareerPath.OTHER), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "Üniversite Değerlendirmesi", tags = listOf("Üniversite Sınavı"), isPublic = true
        ),
        BachelorProgram(
            id = "pisa__iph_european_languages_and_literatures", universityId = "pisa", universityName = "University of Pisa",
            city = "Pisa", region = "Toscana",
            programName = "IPH European Languages and Literatures", fieldCategory = FieldCategory.HUMANITIES,
            relatedCareers = listOf(CareerPath.OTHER), primaryStudyArea = ExamType.CENT_S,
            admissionExam = "Üniversite Değerlendirmesi", tags = listOf("Üniversite Sınavı"), isPublic = true
        ),
    )
}

